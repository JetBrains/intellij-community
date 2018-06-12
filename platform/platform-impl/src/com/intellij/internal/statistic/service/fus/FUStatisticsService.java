// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.service.ConfigurableStatisticsService;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsAggregator;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsStateService;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;
import java.util.Set;

public class FUStatisticsService extends ConfigurableStatisticsService<FUStatisticsSettingsService> {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.service.whiteList.FUStatisticsService");

  private static final FUStatisticsSettingsService mySettingsService = FUStatisticsSettingsService.getInstance();
  private static final FUStatisticsAggregator myAggregator = FUStatisticsAggregator.create();

  @Override
  @NotNull
  protected String sendData() {
    String serviceUrl = mySettingsService.getServiceUrl();
    if (serviceUrl == null) {
      throw new StatServiceException("Unknown Statistics Server URL");
    }

    Set<String> approvedGroups = mySettingsService.getApprovedGroups();
    if (approvedGroups.isEmpty() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        throw new StatServiceException("There are no approved collectors or Statistics White List Service is unavailable.");
    }
    FSContent allDataFromCollectors = myAggregator.getUsageCollectorsData(approvedGroups);
    if (allDataFromCollectors == null) {
      throw new StatServiceException("There are no data from collectors to send");
    }

    try {
      String dataToSend = FUStatisticsStateService.create().getMergedDataToSend(allDataFromCollectors.asJsonString(), approvedGroups);
      if (dataToSend == null) {
        throw new StatServiceException("There are no data from collectors to send");
      }

      HttpRequests.post(serviceUrl, HttpRequests.JSON_CONTENT_TYPE)
                  .isReadResponseOnError(true)
                  .connect(request -> {
                    request.write(dataToSend);
                    if (LOG.isDebugEnabled()) {
                      String message = request.readString();
                      if (message.isEmpty()) {
                        LOG.debug(Integer.toString(((HttpURLConnection)request.getConnection()).getResponseCode()));
                      }
                      else {
                        LOG.debug(message);
                      }
                    }
                    return null;
                  });

      FUStatisticsPersistence.clearSessionPersistence(System.currentTimeMillis());
      FUStatisticsPersistence.persistSentData(dataToSend);
      FUStatisticsPersistence.persistDataFromCollectors(allDataFromCollectors.asJsonString());

      return dataToSend;
    }
    catch (HttpRequests.HttpStatusException e) {
      String responseMessage = e.getMessage();
      LOG.info(responseMessage);
      throw new StatServiceException("Error during data sending. \n " + responseMessage);
    }
    catch (Exception e) {
      LOG.info(e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  @Override
  public FUStatisticsSettingsService getConnectionService() {
    return mySettingsService;
  }
}
