// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection;

import com.intellij.internal.statistic.config.EventLogOptions;
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpResponse;
import com.intellij.internal.statistic.eventLog.connection.request.StatsRequestBuilder;
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.isEmpty;

@ApiStatus.Internal
public class EventLogStatisticsService implements StatisticsService {

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

    MachineId machineId = getMachineId(device, settings);
    try {
      EventLogConnectionSettings connectionSettings = info.getConnectionSettings();

      decorator.onLogsLoaded(logs.size());
      final List<File> toRemove = new ArrayList<>(logs.size());
      for (EventLogFile logFile : logs) {
        File file = logFile.getFile();
        EventLogBuildType type = logFile.getType(defaultBuildType);
        LogEventFilter filter = settings.getEventFilter(baseFilter, type);
        String deviceId = device.getDeviceId();
        LogEventRecordRequest recordRequest =
          LogEventRecordRequest.Companion.create(file, config.getRecorderId(), productCode, deviceId, filter, isInternal, logger,
                                                 machineId);
        ValidationErrorInfo error = validate(recordRequest, file);
        if (error != null) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + "-> " + error.getMessage());
          }
          decorator.onFailed(recordRequest, error.getCode(), null);
          toRemove.add(file);
          continue;
        }

        try {
          StatsHttpRequests.post(serviceUrl, connectionSettings).
            withBody(LogEventSerializer.INSTANCE.toString(recordRequest), "application/json", StandardCharsets.UTF_8).
            succeed((r, code) -> {
              toRemove.add(file);
              decorator.onSucceed(recordRequest, loadAndLogResponse(logger, r, file), file.getAbsolutePath());
            }).
            fail((r, code) -> {
              if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
                toRemove.add(file);
              }
              decorator.onFailed(recordRequest, code, loadAndLogResponse(logger, r, file));
            }).send();
        }
        catch (Exception e) {
          if (logger.isTraceEnabled()) {
            logger.trace(file.getName() + " -> " + e.getMessage());
          }
          //noinspection InstanceofCatchParameter
          int errorCode = e instanceof StatsRequestBuilder.InvalidHttpRequest ? ((StatsRequestBuilder.InvalidHttpRequest)e).getCode() : 50;
          decorator.onFailed(null, errorCode, null);
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

  private static MachineId getMachineId(@NotNull DeviceConfiguration device, @NotNull EventLogSettingsService settings) {
    if (device.getMachineId() == MachineId.DISABLED) {
      return MachineId.DISABLED;
    }
    Map<String, String> options = settings.getOptions();
    String machineIdSaltOption = options.get(EventLogOptions.MACHINE_ID_SALT);
    if (EventLogOptions.MACHINE_ID_DISABLED.equals(machineIdSaltOption)) {
      return MachineId.DISABLED;
    }
    return device.getMachineId();
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
  private static ValidationErrorInfo validate(@Nullable LogEventRecordRequest request, @NotNull File file) {
    if (request == null) {
      return new ValidationErrorInfo("File is empty or has invalid format: " + file.getName(), 1);
    }

    if (isEmpty(request.getDevice())) {
      return new ValidationErrorInfo("Cannot upload event log, device ID is empty", 2);
    }
    else if (isEmpty(request.getProduct())) {
      return new ValidationErrorInfo("Cannot upload event log, product code is empty", 3);
    }
    else if (isEmpty(request.getRecorder())) {
      return new ValidationErrorInfo("Cannot upload event log, recorder code is empty", 4);
    }
    else if (request.getRecords().isEmpty()) {
      return new ValidationErrorInfo("Cannot upload event log, record list is empty", 5);
    }

    for (LogEventRecord content : request.getRecords()) {
      if (content.getEvents().isEmpty()) {
        return new ValidationErrorInfo("Cannot upload event log, event list is empty", 6);
      }
    }
    return null;
  }

  @NotNull
  protected static List<EventLogFile> getLogFiles(@NotNull EventLogRecorderConfig provider, @NotNull DataCollectorDebugLogger logger) {
    try {
      return provider.getFilesToSendProvider().getFilesToSend();
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

  private static final class ValidationErrorInfo {
    private final int myCode;
    private final String myError;

    private ValidationErrorInfo(@NotNull String error, int code) {
      myError = error;
      myCode = code;
    }

    private int getCode() {
      return myCode;
    }

    @NotNull
    private String getMessage() {
      return myError;
    }
  }

  private static final class EventLogCounterResultDecorator implements EventLogResultDecorator {
    private final EventLogSendListener myListener;

    private int myLocalFiles = -1;
    private final List<String> mySuccessfullySentFiles = new ArrayList<>();
    private final List<Integer> myErrors = new ArrayList<>();

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
    public void onFailed(@Nullable LogEventRecordRequest request, int error, @Nullable String content) {
      myErrors.add(error);
    }

    @NotNull
    @Override
    public StatisticsResult onFinished() {
      if (myListener != null) {
        myListener.onLogsSend(mySuccessfullySentFiles, myErrors, myLocalFiles);
      }

      int succeed = mySuccessfullySentFiles.size();
      int failed = myErrors.size();
      int total = succeed + failed;
      if (total == 0) {
        return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (failed > 0) {
        return new StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded " + succeed + " out of " + total + " files.");
      }
      return new StatisticsResult(ResultCode.SEND, "Uploaded " + succeed + " files.");
    }
  }
}
