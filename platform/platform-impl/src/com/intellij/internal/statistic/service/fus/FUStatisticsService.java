// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.google.gson.Gson;
import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.service.ConfigurableStatisticsService;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsAggregator;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
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

public class FUStatisticsService extends ConfigurableStatisticsService<FUStatisticsSettingsService> {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.service.whiteList.FUStatisticsService");

  private static final FUStatisticsSettingsService myConnections = FUStatisticsSettingsService.getInstance();
  private static final FUStatisticsAggregator myAggregator = FUStatisticsAggregator.create();

  @Override
  protected String sendData() {

    String serviceUrl = myConnections.getServiceUrl();
    if (serviceUrl == null) return null;

    FSContent gsonContent = myAggregator.getUsageCollectorsData(myConnections.getApprovedGroups());

    if (gsonContent == null) return null;
    try {
      Gson gson = new Gson();
      HttpClient httpClient = HttpClientBuilder.create().build();
      String content = gson.toJson(gsonContent);

      HttpPost post = new HttpPost(serviceUrl);
      StringEntity postingString = new StringEntity(content);
      post.setEntity(postingString);
      post.setHeader("Content-type", "application/json");
      HttpResponse response = httpClient.execute(post);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new StatServiceException("Error during data sending... Code: " + response.getStatusLine().getStatusCode());
      }

      FUStatisticsPersistence.clearSessionPersistence(System.currentTimeMillis());

      if (LOG.isDebugEnabled()) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          LOG.debug(StreamUtil.readText(entity.getContent(), CharsetToolkit.UTF8));
        }
      }
      return content;
    }
    catch (StatServiceException e) {
      throw e;
    }
    catch (Exception e) {
      throw new StatServiceException("Error during data sending...", e);
    }
  }

  @Override
  public FUStatisticsSettingsService getConnectionService() {
    return myConnections;
  }
}
