// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EventLogStatisticsService implements StatisticsService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogStatisticsService");
  private static final ContentType APPLICATION_JSON = ContentType.create("application/json", Consts.UTF_8);

  private static final EventLogStatisticsSettingsService mySettingsService = EventLogStatisticsSettingsService.getInstance();

  @Override
  public StatisticsResult send() {
    if (!FeatureUsageLogger.INSTANCE.isEnabled()) {
      throw new StatServiceException("Event Log collector is not enabled");
    }

    final String serviceUrl = mySettingsService.getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    if (!mySettingsService.isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    try {
      int succeed = 0;
      final List<File> logs = FeatureUsageLogger.INSTANCE.getLogFiles();
      final List<File> toRemove = new ArrayList<>(logs.size());
      for (File file : logs) {
        final LogEventRecordRequest request = LogEventRecordRequest.Companion.create(file);
        final String error = validate(request, file);
        if (StringUtil.isNotEmpty(error) || request == null) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + "-> " + error);
          }
          toRemove.add(file);
          continue;
        }

        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpPost post = createPostRequest(serviceUrl, LogEventSerializer.INSTANCE.toString(request));
        final HttpResponse response = httpClient.execute(post);

        final int code = response.getStatusLine().getStatusCode();
        if (code == HttpStatus.SC_OK) {
          succeed++;
          toRemove.add(file);
        }
        else if (code == HttpStatus.SC_BAD_REQUEST) {
          toRemove.add(file);
        }

        if (LOG.isTraceEnabled()) {
          LOG.trace(file.getName() + " -> " + getResponseMessage(response));
        }
      }

      cleanupSentFiles(toRemove);

      UsageStatisticsPersistenceComponent.getInstance().setEventLogSentTime(System.currentTimeMillis());
      if (logs.isEmpty()) {
        return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (succeed != logs.size()) {
        return new StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded " + succeed + " out of " + logs.size() + " files.");
      }
      return new StatisticsResult(ResultCode.SEND, "Uploaded " + succeed + " files.");
    }
    catch (Exception e) {
      LOG.info(e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  @Nullable
  private static String validate(@Nullable LogEventRecordRequest request, @NotNull File file) {
    if (request == null) {
      return "File is empty or has invalid format: " + file.getName();
    }

    if (StringUtil.isEmpty(request.getUser())) {
      return "Cannot upload event log, user ID is empty";
    }
    else if (StringUtil.isEmpty(request.getProduct())) {
      return "Cannot upload event log, product code is empty";
    }
    else if (request.getRecords().isEmpty()) {
      return "Cannot upload event log, record list is empty";
    }

    for (LogEventRecord content : request.getRecords()) {
      if (content.getEvents().isEmpty()) {
        return "Cannot upload event log, event list is empty";
      }
    }
    return null;
  }

  public void cleanupSentFiles(@NotNull List<File> toRemove) {
    for (File file : toRemove) {
      if (!file.delete()) {
        LOG.warn("Failed deleting event log: " + file.getName());
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("Removed sent log: " + file.getName());
      }
    }
  }

  @NotNull
  public static HttpPost createPostRequest(@NotNull String serviceUrl, @NotNull String content) {
    final HttpPost post = new HttpPost(serviceUrl);
    final StringEntity postingString = new StringEntity(content, APPLICATION_JSON);
    post.setEntity(postingString);
    return post;
  }

  @Override
  public Notification createNotification(@NotNull String groupDisplayId, @Nullable NotificationListener listener) {
    return null;
  }

  @NotNull
  private static String getResponseMessage(HttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      return StreamUtil.readText(entity.getContent(), CharsetToolkit.UTF8);
    }
    return Integer.toString(response.getStatusLine().getStatusCode());
  }
}
