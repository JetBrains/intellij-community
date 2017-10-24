/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsHttpClientSender;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemotelyConfigurableStatServiceTest {
  private static String STAT_URL;
  private static String STAT_CONFIG_URL;

  @BeforeClass
  public static void init() throws Exception {
    int port = NetUtils.findAvailableSocketPort();
    STAT_URL = "http://localhost:" + port + "/stat.jsp";
    STAT_CONFIG_URL = "http://localhost:" + port + "/config.jsp";
  }

  @Test
  public void testStatisticsConnectionServiceDefaultSettings() {
    StatisticsConnectionService connectionService = new StatisticsConnectionService(STAT_CONFIG_URL, STAT_URL);
    assertEquals(STAT_URL, connectionService.getServiceUrl());

    assertTrue(connectionService.isTransmissionPermitted());
    String[] attributeNames = connectionService.getAttributeNames();

    assertEquals(3, attributeNames.length);
    assertEquals("url", attributeNames[0]);
    assertEquals("permitted", attributeNames[1]);
    assertEquals("disabled", attributeNames[2]);
  }

  @Test
  public void testEmptyDataSending() {
    RemotelyConfigurableStatisticsService service =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(),
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant() {
                                                  @Override
                                                  public String getData(@NotNull Set<String> disabledGroups) {
                                                    return "";
                                                  }
                                                });
    StatisticsResult result = service.send();
    assertEquals(StatisticsResult.ResultCode.NOTHING_TO_SEND, result.getCode());
  }

  @Test
  public void testIncorrectUrlSending() {
    RemotelyConfigurableStatisticsService service =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(STAT_CONFIG_URL, STAT_URL),
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant() {
                                                  @Override
                                                  public String getData(@NotNull Set<String> disabledGroups) {
                                                    return "group:key1=11";
                                                  }
                                                });
    StatisticsResult result = service.send();
    assertEquals(StatisticsResult.ResultCode.SENT_WITH_ERRORS, result.getCode());
  }

  @Test
  public void testRemotelyDisabledTransmission() {
    RemotelyConfigurableStatisticsService service =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService() {
                                                  @Override
                                                  public boolean isTransmissionPermitted() {
                                                    return false;
                                                  }
                                                },
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant());
    StatisticsResult result = service.send();
    assertEquals(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, result.getCode());
  }

  @Test
  public void testErrorInRemoteConfiguration() {
    RemotelyConfigurableStatisticsService service =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(STAT_CONFIG_URL, null),
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant());
    StatisticsResult result = service.send();
    assertEquals(StatisticsResult.ResultCode.ERROR_IN_CONFIG, result.getCode());
  }
}
