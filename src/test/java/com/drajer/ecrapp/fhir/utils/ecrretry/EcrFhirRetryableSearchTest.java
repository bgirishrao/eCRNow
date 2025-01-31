package com.drajer.ecrapp.fhir.utils.ecrretry;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.gclient.*;
import com.drajer.ecrapp.config.SpringConfiguration;
import com.drajer.ecrapp.fhir.utils.FHIRRetryTemplate;
import com.drajer.ecrapp.fhir.utils.FHIRRetryTemplateConfig;
import com.drajer.ecrapp.fhir.utils.RetryableException;
import com.drajer.sof.model.ClientDetails;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.utils.FhirContextInitializer;
import com.drajer.test.util.TestUtils;
import java.util.*;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EcrFhirRetryableSearchTest {

  private LaunchDetails currentStateDetails;
  private ClientDetails clientDetails;

  @InjectMocks FHIRRetryTemplate fhirretryTemplate;

  @InjectMocks SpringConfiguration springConfiguration;
  @InjectMocks FHIRRetryTemplateConfig fhirRetryTemplateConfig;
  @Mock FhirContextInitializer fhirContextInitializer;
  @InjectMocks FHIRRetryTemplateConfig.HttpMethodType httpMethodType;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    currentStateDetails =
        (LaunchDetails)
            TestUtils.getResourceAsObject(
                "R4/Misc/LaunchDetails/LaunchDetails.json", LaunchDetails.class);
    currentStateDetails.setLastUpdated(new Date());
    clientDetails =
        (ClientDetails)
            TestUtils.getResourceAsObject(
                "R4/Misc/ClientDetails/ClientDetail_IT_FullECR.json", ClientDetails.class);
  }

  @Test
  public void testRetrySearch() {
    FhirContext context = mock(FhirContext.class);
    EcrFhirRetryClient retryClient = mock(EcrFhirRetryClient.class);
    IQuery iQuery = mock(EcrFhirRetryableSearch.class);
    IUntypedQuery iUntypedQuery = mock(EcrFhirRetryableSearch.class);
    String url = "http://localhost:9010/FHIR/Condition?patient=12742571";
    Map<String, FHIRRetryTemplateConfig.HttpMethodType> map = new HashMap<>();
    currentStateDetails.setFhirVersion("R4");

    httpMethodType.setMaxRetries(3);
    httpMethodType.setRetryWaitTime(3000);
    httpMethodType.setRetryStatusCodes(
        new ArrayList<>(Arrays.asList(408, 429, 502, 503, 504, 500)));

    map.put("GET", httpMethodType);
    fhirRetryTemplateConfig.setHttpMethodTypeMap(map);
    fhirRetryTemplateConfig.setMaxRetries(3);
    fhirRetryTemplateConfig.setRetryWaitTime(3000);

    springConfiguration.setFhirRetryTemplateConfig(fhirRetryTemplateConfig);
    fhirretryTemplate.setRetryTemplate(springConfiguration.retryTemplate());
    when(retryClient.getRetryTemplate()).thenReturn(fhirretryTemplate);

    when(fhirContextInitializer.getFhirContext(currentStateDetails.getFhirVersion()))
        .thenReturn(context);
    when(fhirContextInitializer.createClient(context, currentStateDetails)).thenReturn(retryClient);

    when(retryClient.search()).thenReturn(iUntypedQuery);
    when(iUntypedQuery.byUrl(url)).thenReturn(iQuery);
    when(iQuery.returnBundle(Bundle.class)).thenReturn(iQuery);
    when(iQuery.execute()).thenThrow(new RetryableException("INTERNAL_SERVER_ERROR", 500, "GET"));

    try {
      retryClient
          .getRetryTemplate()
          .execute(
              retryContext -> {
                return iQuery.execute();
              },
              null);
    } catch (Exception e) {
      verify(iQuery, times(3)).execute();
    }
  }
}
