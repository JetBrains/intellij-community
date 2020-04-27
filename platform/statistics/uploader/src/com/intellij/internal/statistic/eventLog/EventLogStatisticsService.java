// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.connect.StatisticsService;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventLogStatisticsService implements StatisticsService {
  private static final ContentType APPLICATION_JSON = ContentType.create("application/json", Consts.UTF_8);

  private static final int MAX_FILES_TO_SEND = 5;

  private final DeviceConfiguration myDeviceConfiguration;
  private final EventLogSettingsService mySettingsService;
  private final EventLogRecorderConfig myRecorderConfiguration;

  private final EventLogSendListener mySendListener;

  public EventLogStatisticsService(@NotNull DeviceConfiguration device,
                                   @NotNull EventLogRecorderConfig config,
                                   @NotNull EventLogApplicationInfo application,
                                   @Nullable EventLogSendListener listener) {
    myDeviceConfiguration = device;
    myRecorderConfiguration = config;
    mySettingsService = new EventLogUploadSettingsService(config.getRecorderId(), application);
    mySendListener = listener;
  }

  @Override
  public StatisticsResult send() {
    return send(myDeviceConfiguration, myRecorderConfiguration, mySettingsService, new EventLogCounterResultDecorator(mySendListener));
  }

  public StatisticsResult send(@NotNull EventLogResultDecorator decorator) {
    return send(myDeviceConfiguration, myRecorderConfiguration, mySettingsService, decorator);
  }

  public static StatisticsResult send(@NotNull DeviceConfiguration device,
                                      @NotNull EventLogRecorderConfig config,
                                      @NotNull EventLogSettingsService settings,
                                      @NotNull EventLogResultDecorator decorator) {
    final EventLogApplicationInfo info = settings.getApplicationInfo();
    final DataCollectorDebugLogger logger = info.getLogger();

    final List<EventLogFile> logs = getLogFiles(config, logger);
    if (!config.isSendEnabled()) {
      cleanupEventLogFiles(logs, logger);
      return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "Event Log collector is not enabled");
    }

    if (logs.isEmpty()) {
      return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to send");
    }

    final String serviceUrl = settings.getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    if (!isSendLogsEnabled(device, settings.getPermittedTraffic())) {
      cleanupEventLogFiles(logs, logger);
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    final boolean isInternal = info.isInternal();
    final String productCode = info.getProductCode();
    final LogEventFilter filter = settings.getEventFilter();
    try {
      decorator.onLogsLoaded(logs.size());
      final List<File> toRemove = new ArrayList<>(logs.size());
      int size = Math.min(MAX_FILES_TO_SEND, logs.size());
      for (int i = 0; i < size; i++) {
        final File file = logs.get(i).getFile();
        final String deviceId = device.getDeviceId();
        final LogEventRecordRequest recordRequest =
          LogEventRecordRequest.Companion.create(file, config.getRecorderId(), productCode, deviceId, filter, isInternal, logger);
        final String error = validate(recordRequest, file);
        if (StatisticsEventLogUtil.isNotEmpty(error) || recordRequest == null) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + "-> " + error);
          }
          decorator.onFailed(recordRequest);
          toRemove.add(file);
          continue;
        }

        try {
          HttpResponse response = execute(info.getUserAgent(), serviceUrl, recordRequest);
          int code = response.getStatusLine().getStatusCode();
          if (code == HttpStatus.SC_OK) {
            decorator.onSucceed(recordRequest);
            toRemove.add(file);
          }
          else {
            decorator.onFailed(recordRequest);
            if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
              toRemove.add(file);
            }
          }

          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + " -> " + getResponseMessage(response));
          }
        }
        catch (Exception e) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + " -> " + e.getMessage());
          }
        }
      }

      cleanupFiles(toRemove, logger);
      return decorator.onFinished();
    }
    catch (Exception e) {
      final String message = e.getMessage();
      logger.info(message != null ? message : "", e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  @NotNull
  private static HttpResponse execute(@NotNull String userAgent, String serviceUrl, LogEventRecordRequest recordRequest) throws IOException {
    HttpPost post = new HttpPost(serviceUrl);
    post.setEntity(new GzipCompressingEntity(new StringEntity(LogEventSerializer.INSTANCE.toString(recordRequest), APPLICATION_JSON)));
    return StatisticsEventLogUtil.create(userAgent).execute(post);
  }

  @NotNull
  private static String getResponseMessage(HttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      return EntityUtils.toString(entity, StatisticsEventLogUtil.UTF8);
    }
    return Integer.toString(response.getStatusLine().getStatusCode());
  }

  private static boolean isSendLogsEnabled(@NotNull DeviceConfiguration userData, int percent) {
    if (percent == 0) {
      return false;
    }
    return userData.getBucket() < percent * 2.56;
  }

  @Nullable
  private static String validate(@Nullable LogEventRecordRequest request, @NotNull File file) {
    if (request == null) {
      return "File is empty or has invalid format: " + file.getName();
    }

    if (StatisticsEventLogUtil.isEmpty(request.getDevice())) {
      return "Cannot upload event log, device ID is empty";
    }
    else if (StatisticsEventLogUtil.isEmpty(request.getProduct())) {
      return "Cannot upload event log, product code is empty";
    }
    else if (StatisticsEventLogUtil.isEmpty(request.getRecorder())) {
      return "Cannot upload event log, recorder code is empty";
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

  @NotNull
  protected static List<EventLogFile> getLogFiles(@NotNull EventLogRecorderConfig provider, @NotNull DataCollectorDebugLogger logger) {
    try {
      return provider.getLogFilesProvider().getLogFiles();
    }
    catch (Exception e) {
      final String message = e.getMessage();
      logger.info(message != null ? message : "", e);
    }
    return Collections.emptyList();
  }

  private static void cleanupEventLogFiles(@NotNull List<EventLogFile> toRemove, @NotNull DataCollectorDebugLogger logger) {
    List<File> filesToRemove = new ArrayList<>();
    for (EventLogFile file : toRemove) {
      filesToRemove.add(file.getFile());
    }
    cleanupFiles(filesToRemove, logger);
  }

  private static void cleanupFiles(@NotNull List<File> toRemove, @NotNull DataCollectorDebugLogger logger) {
    for (File file : toRemove) {
      if (!file.delete()) {
        logger.warn("Failed deleting event log: " + file.getName());
      }

      if (logger.isTraceEnabled()) {
        logger.trace("Removed sent log: " + file.getName());
      }
    }
  }

  private static class EventLogCounterResultDecorator implements EventLogResultDecorator {
    private final EventLogSendListener myListener;

    private int myLocalFiles = -1;
    private int myFailed = 0;
    private int mySucceed = 0;

    private EventLogCounterResultDecorator(@Nullable EventLogSendListener listener) {
      myListener = listener;
    }

    @Override
    public void onLogsLoaded(int localFiles) {
      myLocalFiles = localFiles;
    }

    @Override
    public void onSucceed(@NotNull LogEventRecordRequest request) {
      mySucceed++;
    }

    @Override
    public void onFailed(@Nullable LogEventRecordRequest request) {
      myFailed++;
    }

    @NotNull
    @Override
    public StatisticsResult onFinished() {
      if (myListener != null) {
        myListener.onLogsSend(mySucceed, myFailed, myLocalFiles);
      }

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
