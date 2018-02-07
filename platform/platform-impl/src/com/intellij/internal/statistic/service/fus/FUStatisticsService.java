// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.service.ConfigurableStatisticsService;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsAggregator;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsStateService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
    if (approvedGroups.isEmpty()) {
        throw new StatServiceException("There are no approved collectors or Statistics White List Service is unavailable.");
    }
    FSContent allDataFromCollectors = myAggregator.getUsageCollectorsData(approvedGroups);
    if (allDataFromCollectors == null) {
      throw new StatServiceException("There are no data from collectors to send");
    }

    try {
      String dataToSend = FUStatisticsStateService.create().getMergedDataToSend(allDataFromCollectors.asJsonString());
      if (dataToSend == null) {
        throw new StatServiceException("There are no data from collectors to send");
      }

      HttpResponse response = postStatistics(serviceUrl, dataToSend);

      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        String responseMessage = getResponseMessage(response);
        LOG.info(responseMessage);
        throw new StatServiceException("Error during data sending. \n " + responseMessage);
      }
      FUStatisticsPersistence.clearSessionPersistence(System.currentTimeMillis());
      FUStatisticsPersistence.persistSentData(dataToSend);
      FUStatisticsPersistence.persistDataFromCollectors(allDataFromCollectors.asJsonString());

      if (LOG.isDebugEnabled()) LOG.debug(getResponseMessage(response));

      return dataToSend;
    }  catch (Exception e) {
      LOG.info(e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  private static HttpResponse postStatistics(String serviceUrl, String content) throws IOException {
    HttpClient httpClient = HttpClientBuilder.create().build();
    HttpPost post = new HttpPost(serviceUrl);
    StringEntity postingString = new StringEntity(content);
    post.setEntity(postingString);
    post.setHeader("Content-type", "application/json");
    return httpClient.execute(post);
  }

  @NotNull
  private static String getResponseMessage(HttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      return StreamUtil.readText(entity.getContent(), CharsetToolkit.UTF8);
    }
    return Integer.toString(response.getStatusLine().getStatusCode());
  }

  @Override
  public FUStatisticsSettingsService getConnectionService() {
    return mySettingsService;
  }
}
