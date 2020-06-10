// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter;
import com.intellij.internal.statistic.service.request.StatsHttpRequests;
import com.intellij.internal.statistic.service.request.StatsHttpResponse;
import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.internal.statistic.StatisticsStringUtil.isEmpty;
import static com.intellij.internal.statistic.StatisticsStringUtil.isNotEmpty;

@ApiStatus.Internal
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

  @TestOnly
  public EventLogStatisticsService(@NotNull DeviceConfiguration device,
                                   @NotNull EventLogRecorderConfig config,
                                   @Nullable EventLogSendListener listener,
                                   @Nullable EventLogUploadSettingsService settingsService) {
    myDeviceConfiguration = device;
    myRecorderConfiguration = config;
    mySettingsService = settingsService;
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

    if (!settings.isSettingsReachable()) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: settings server is unreachable");
    }

    if (!settings.isSendEnabled()) {
      cleanupEventLogFiles(logs, logger);
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    final String serviceUrl = settings.getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    final boolean isInternal = info.isInternal();
    final String productCode = info.getProductCode();
    EventLogBuildType defaultBuildType = getDefaultBuildType(info);
    LogEventFilter baseFilter = settings.getBaseEventFilter();
    try {
      decorator.onLogsLoaded(logs.size());
      final List<File> toRemove = new ArrayList<>(logs.size());
      int size = Math.min(MAX_FILES_TO_SEND, logs.size());
      for (int i = 0; i < size; i++) {
        EventLogFile logFile = logs.get(i);
        File file = logFile.getFile();
        EventLogBuildType type = logFile.getType(defaultBuildType);
        LogEventFilter filter = settings.getEventFilter(baseFilter, type);
        String deviceId = device.getDeviceId();
        LogEventRecordRequest recordRequest =
          LogEventRecordRequest.Companion.create(file, config.getRecorderId(), productCode, deviceId, filter, isInternal, logger);
        final String error = validate(recordRequest, file);
        if (isNotEmpty(error) || recordRequest == null) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + "-> " + error);
          }
          decorator.onFailed(recordRequest, null);
          toRemove.add(file);
          continue;
        }

        try {
          StatsHttpRequests.post(serviceUrl, info.getUserAgent()).
            withBody(LogEventSerializer.INSTANCE.toString(recordRequest), APPLICATION_JSON).
            succeed((r, code) -> {
              toRemove.add(file);
              decorator.onSucceed(recordRequest, loadAndLogResponse(logger, r, file), file.getAbsolutePath());
            }).
            fail((r, code) -> {
              if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
                toRemove.add(file);
              }
              decorator.onFailed(recordRequest, loadAndLogResponse(logger, r, file));
            }).send();
        }
        catch (Exception e) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + " -> " + e.getMessage());
          }
          decorator.onFailed(null, null);
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
  private static EventLogBuildType getDefaultBuildType(EventLogApplicationInfo info) {
    return info.isEAP() ? EventLogBuildType.EAP : EventLogBuildType.RELEASE;
  }

  @NotNull
  private static String loadAndLogResponse(@NotNull DataCollectorDebugLogger logger,
                                           @NotNull StatsHttpResponse response,
                                           @NotNull File file) throws IOException {
    String message = response.readAsString();
    String content = message != null ? message : Integer.toString(response.getStatusCode());

    if (logger.isTraceEnabled()) {
      logger.trace(file.getName() + " -> " + content);
    }
    return content;
  }

  @Nullable
  private static String validate(@Nullable LogEventRecordRequest request, @NotNull File file) {
    if (request == null) {
      return "File is empty or has invalid format: " + file.getName();
    }

    if (isEmpty(request.getDevice())) {
      return "Cannot upload event log, device ID is empty";
    }
    else if (isEmpty(request.getProduct())) {
      return "Cannot upload event log, product code is empty";
    }
    else if (isEmpty(request.getRecorder())) {
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
    private final List<String> mySuccessfullySentFiles = new ArrayList<>();

    private EventLogCounterResultDecorator(@Nullable EventLogSendListener listener) {
      myListener = listener;
    }

    @Override
    public void onLogsLoaded(int localFiles) {
      myLocalFiles = localFiles;
    }

    @Override
    public void onSucceed(@NotNull LogEventRecordRequest request, @NotNull String content, @NotNull String logPath) {
      mySuccessfullySentFiles.add(logPath);
    }

    @Override
    public void onFailed(@Nullable LogEventRecordRequest request, @Nullable String content) {
      myFailed++;
    }

    @NotNull
    @Override
    public StatisticsResult onFinished() {
      if (myListener != null) {
        myListener.onLogsSend(mySuccessfullySentFiles, myFailed, myLocalFiles);
      }

      int succeed = mySuccessfullySentFiles.size();
      int total = succeed + myFailed;
      if (total == 0) {
        return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (myFailed > 0) {
        return new StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded " + succeed + " out of " + total + " files.");
      }
      return new StatisticsResult(ResultCode.SEND, "Uploaded " + succeed + " files.");
    }
  }
}
