package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.StatisticsHttpClientSender;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsResult;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

public class RemotelyConfigurableStatServiceTest extends TestCase {

  @NonNls
  private static final String STAT_URL = "http://localhost:8080/stat.jsp";

  @NonNls
  private static final String STAT_CONFIG_URL = "http://localhost:8080/config.jsp";

  public void testStatisticsConnectionServiceDefaultSettings() {
    final StatisticsConnectionService connectionService = new StatisticsConnectionService(STAT_CONFIG_URL, STAT_URL);

    Assert.assertEquals(STAT_URL, connectionService.getServiceUrl());
    Assert.assertTrue(connectionService.isTransmissionPermitted());
    final String[] attributeNames = connectionService.getAttributeNames();

    Assert.assertEquals(attributeNames.length, 2);
    Assert.assertEquals(attributeNames[0], "url");
    Assert.assertEquals(attributeNames[1], "permitted");
  }

  public void testEmptyDataSending() {
    RemotelyConfigurableStatisticsService service = new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(),
                                                                                              new StatisticsHttpClientSender(),
                                                                                              new StatisticsUploadAssistant() {
                                                                                                @Override
                                                                                                public String getData() {
                                                                                                  return "";
                                                                                                }
                                                                                              });
    final StatisticsResult result = service.send();
    Assert.assertEquals(StatisticsResult.ResultCode.NOTHING_TO_SEND, result.getCode());
  }

  public void testIncorrectUrlSending() {
    RemotelyConfigurableStatisticsService service = new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(STAT_CONFIG_URL, STAT_URL),
                                                                                              new StatisticsHttpClientSender(),
                                                                                              new StatisticsUploadAssistant() {
                                                                                                @Override
                                                                                                public String getData() {
                                                                                                  return "group:key1=11";
                                                                                                }
                                                                                              });
    final StatisticsResult result = service.send();
    Assert.assertEquals(StatisticsResult.ResultCode.SENT_WITH_ERRORS, result.getCode());
  }

  public void testRemotelyDisabledTransmission() {
    RemotelyConfigurableStatisticsService service = new RemotelyConfigurableStatisticsService(new StatisticsConnectionService() {
      @Override
      public Boolean isTransmissionPermitted() {
        return false;
      }
    }, new StatisticsHttpClientSender(),
    new StatisticsUploadAssistant());

    final StatisticsResult result = service.send();
    Assert.assertEquals(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, result.getCode());
  }

  public void testErrorInRemoteConfiguration() {
    RemotelyConfigurableStatisticsService service =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(STAT_CONFIG_URL, null),
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant());
    final StatisticsResult result = service.send();
    Assert.assertEquals(StatisticsResult.ResultCode.ERROR_IN_CONFIG, result.getCode());
  }
}
