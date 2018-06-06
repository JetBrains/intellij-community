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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class EventLogStatisticsService implements StatisticsService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogStatisticsService");

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

    if (mySettingsService.getPermittedTraffic() == 0) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    try {
      int succeed = 0;
      final List<File> logs = FeatureUsageLogger.INSTANCE.getLogFiles();
      final List<File> toRemove = new ArrayList<>(logs.size());
      for (File file : logs) {
        final LogEventRecordRequest recordRequest = LogEventRecordRequest.Companion.create(file);
        final String error = validate(recordRequest, file);
        if (StringUtil.isNotEmpty(error) || recordRequest == null) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + "-> " + error);
          }
          toRemove.add(file);
          continue;
        }

        try {
          HttpRequests
            .post(serviceUrl, HttpRequests.JSON_CONTENT_TYPE)
            .isReadResponseOnError(true)
            .tuner(connection -> connection.setRequestProperty("Content-Encoding", "gzip"))
            .connect(request -> {
              final BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
              try (OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(out))) {
                LogEventSerializer.INSTANCE.toString(recordRequest, writer);
              }
              request.write(out.toByteArray());
              if (LOG.isTraceEnabled()) {
                LOG.trace(file.getName() + " -> " + request.readString());
              }
              return null;
            });
          succeed++;
          toRemove.add(file);
        }
        catch (HttpRequests.HttpStatusException e) {
          if (e.getStatusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
            toRemove.add(file);
          }

          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + " -> " + e.getMessage());
          }
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

  @Override
  public Notification createNotification(@NotNull String groupDisplayId, @Nullable NotificationListener listener) {
    return null;
  }
}
