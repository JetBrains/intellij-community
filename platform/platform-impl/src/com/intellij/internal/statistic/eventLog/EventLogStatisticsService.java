// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.PermanentInstallationID;
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
  private static final EventLogSettingsService mySettingsService = EventLogExternalSettingsService.getInstance();

  @Override
  public StatisticsResult send() {
    return send(mySettingsService, new EventLogCounterResultDecorator());
  }

  public static StatisticsResult send(@NotNull EventLogSettingsService settings, @NotNull EventLogResultDecorator decorator) {
    if (!FeatureUsageLogger.INSTANCE.isEnabled()) {
      throw new StatServiceException("Event Log collector is not enabled");
    }

    final String serviceUrl = settings.getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    if (!isSendLogsEnabled(settings.getPermittedTraffic())) {
      cleanupAllFiles();
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    final LogEventFilter filter = settings.getEventFilter();
    try {
      final List<File> logs = FeatureUsageLogger.INSTANCE.getLogFiles();
      final List<File> toRemove = new ArrayList<>(logs.size());
      for (File file : logs) {
        final LogEventRecordRequest recordRequest = LogEventRecordRequest.Companion.create(file, filter);
        final String error = validate(recordRequest, file);
        if (StringUtil.isNotEmpty(error) || recordRequest == null) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + "-> " + error);
          }
          decorator.failed(recordRequest);
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
                LOG.trace(file.getName() + " -> " + readResponse(request));
              }
              return null;
            });
          decorator.succeed(recordRequest);
          toRemove.add(file);
        }
        catch (HttpRequests.HttpStatusException e) {
          decorator.failed(recordRequest);
          if (e.getStatusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
            toRemove.add(file);
          }

          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + " -> " + e.getMessage());
          }
        }
        catch (Exception e) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(file.getName() + " -> " + e.getMessage());
          }
        }
      }

      cleanupFiles(toRemove);

      UsageStatisticsPersistenceComponent.getInstance().setEventLogSentTime(System.currentTimeMillis());
      return decorator.toResult();
    }
    catch (Exception e) {
      LOG.info(e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  @Nullable
  private static String readResponse(@NotNull HttpRequests.Request request) {
    try {
      return request.readString();
    }
    catch (Exception e) {
      return e.getMessage();
    }
  }

  private static boolean isSendLogsEnabled(int percent) {
    if (percent == 0) {
      return false;
    }
    final String userId = PermanentInstallationID.get();
    return (Math.abs(userId.hashCode()) % 100) < percent;
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

  private static void cleanupAllFiles() {
    try {
      final List<File> logs = FeatureUsageLogger.INSTANCE.getLogFiles();
      if (!logs.isEmpty()) {
        cleanupFiles(logs);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private static void cleanupFiles(@NotNull List<File> toRemove) {
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

  private static class EventLogCounterResultDecorator implements EventLogResultDecorator {
    private int myFailed = 0;
    private int mySucceed = 0;

    @Override
    public void succeed(@NotNull LogEventRecordRequest request) {
      mySucceed++;
    }

    @Override
    public void failed(@Nullable LogEventRecordRequest request) {
      myFailed++;
    }

    @NotNull
    @Override
    public StatisticsResult toResult() {
      int total = mySucceed + myFailed;
      if (total == 0) {
        return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (myFailed > 0) {
        return new StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded " + mySucceed + " out of " + total + " files.");
      }
      return new StatisticsResult(ResultCode.SEND, "Uploaded " + mySucceed + " files.");
    }
  }
}
