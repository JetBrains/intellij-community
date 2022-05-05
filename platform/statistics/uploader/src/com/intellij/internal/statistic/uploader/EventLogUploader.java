// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalApplicationInfo;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalRecorderConfig;
import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener;
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult;
import com.intellij.internal.statistic.uploader.events.ExternalEventsLogger;
import com.intellij.internal.statistic.uploader.util.ExtraHTTPHeadersParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.split;

public final class EventLogUploader {
  private static final int WAIT_FOR_IDE_MS = 2000;

  public static void main(String[] args) {
    DataCollectorDebugLogger logger = new ExternalDataCollectorLogger();
    ExternalEventsLogger eventsLogger = new ExternalEventsLogger();
    try {
      execute(args, logger, eventsLogger);
    }
    catch (Throwable e) {
      logger.warn("Failed uploading logs", e);
      eventsLogger.logSendingLogsFinished("EXCEPTION_OCCURRED");
    }
  }

  private static void execute(String[] args,
                              DataCollectorDebugLogger logger,
                              ExternalEventsLogger eventsLogger) {
    logger.info("Process started with '" + String.join(" ", args) + "'");
    logger.info("Classpath:" + System.getProperty("java.class.path"));

    eventsLogger.logSendingLogsStarted();
    if (args.length == 0) {
      logger.warn("No arguments were found");
      eventsLogger.logSendingLogsFinished("NO_ARGUMENTS");
      return;
    }

    Map<String, String> options = EventLogUploaderCliParser.parseOptions(args);
    EventLogApplicationInfo appInfo = newApplicationInfo(options, logger, eventsLogger);
    if (appInfo == null) {
      logger.warn("Failed creating application info from arguments");
      eventsLogger.logSendingLogsFinished("NO_APPLICATION_CONFIG");
      return;
    }

    List<SendConfiguration> configs = parseSendConfigurations(options);
    if (!waitForIde(logger, options, 20)) {
      logger.warn("Cannot send logs because IDE didn't close during " + (20 * WAIT_FOR_IDE_MS) + "ms");
      eventsLogger.logSendingLogsFinished("IDE_NOT_CLOSING");
      return;
    }

    ExecutorService service = Executors.newFixedThreadPool(configs.size());
    for (SendConfiguration config : configs) {
      service.execute(() -> sendLogsByRecorder(appInfo, config, logger, eventsLogger));
    }

    service.shutdown();
    try {
      service.awaitTermination(5, TimeUnit.MINUTES);
    }
    catch (InterruptedException e) {
      // ignore
    }
  }

  private static void sendLogsByRecorder(@NotNull EventLogApplicationInfo appInfo,
                                         @NotNull SendConfiguration config,
                                         @NotNull DataCollectorDebugLogger logger,
                                         @NotNull ExternalEventsLogger eventsLogger) {
    String recorderId = config.recorderId;
    if (config.deviceConfig == null) {
      logger.warn("[" + recorderId + "] Failed creating device config from arguments");
      eventsLogger.logSendingLogsFinished(recorderId, "NO_DEVICE_CONFIG");
      return;
    }

    if (config.recorderConfig == null) {
      logger.warn("[" + recorderId + "] Failed creating recorder config from arguments");
      eventsLogger.logSendingLogsFinished(recorderId, "NO_RECORDER_CONFIG");
      return;
    }

    logger.info("[" + recorderId + "] Start uploading...");
    EventLogConnectionSettings connectionSettings = appInfo.getConnectionSettings();
    logger.info("[" + recorderId + "] {product:" + appInfo.getProductCode() + ", productVersion:" + appInfo.getProductVersion() +
                ", userAgent:" + connectionSettings.getUserAgent() + ", url: " + appInfo.getTemplateUrl() +
                ", internal:" + appInfo.isInternal() + ", isTest:" + appInfo.isTest() + "}");

    String logs = config.recorderConfig.getFilesToSendProvider().getFilesToSend().stream().
      map(file -> file.getFile().getAbsolutePath()).collect(Collectors.joining(File.pathSeparator));
    logger.info("[" + recorderId + "] {recorder:" + config.recorderConfig.getRecorderId() + ", files:" + logs + "}");
    logger.info("[" + recorderId + "] {device:" + config.deviceConfig.getDeviceId() + ", bucket:" + config.deviceConfig.getBucket() + "}");
    try {
      EventLogStatisticsService service = new EventLogStatisticsService(
        config.deviceConfig, config.recorderConfig, appInfo,
        new EventLogSendListener() {
          @Override
          public void onLogsSend(@NotNull List<String> successfullySentFiles,
                                 @NotNull List<Integer> errors,
                                 int totalLocalFiles) {
            eventsLogger.logSendingLogsSucceed(recorderId, successfullySentFiles, errors, totalLocalFiles);
          }
        });

      StatisticsResult result = service.send();
      eventsLogger.logSendingLogsFinished(recorderId, result.getCode());
      if (logger.isTraceEnabled()) {
        logger.trace("[" + recorderId + "] Uploading finished with " + result.getCode().name());
        logger.trace("[" + recorderId + "] " + result.getDescription());
      }
    }
    catch (Exception e) {
      logger.warn("[" + recorderId + "] Failed sending files: " + e.getMessage());
      eventsLogger.logSendingLogsFinished(recorderId, "ERROR_ON_SEND");
    }
  }

  @Nullable
  private static EventLogApplicationInfo newApplicationInfo(Map<String, String> options,
                                                            DataCollectorDebugLogger logger,
                                                            DataCollectorSystemEventLogger eventLogger) {
    String productCode = options.get(EventLogUploaderOptions.PRODUCT_OPTION);
    String productVersion = options.get(EventLogUploaderOptions.PRODUCT_VERSION_OPTION);
    String url = options.get(EventLogUploaderOptions.URL_OPTION);
    String userAgent = options.get(EventLogUploaderOptions.USER_AGENT_OPTION);
    String headersString = options.get(EventLogUploaderOptions.EXTRA_HEADERS);
    Map<String, String> extraHeaders = ExtraHTTPHeadersParser.parse(headersString);
    if (url != null && productCode != null) {
      boolean isInternal = options.containsKey(EventLogUploaderOptions.INTERNAL_OPTION);
      boolean isTest = options.containsKey(EventLogUploaderOptions.TEST_OPTION);
      boolean isEAP = options.containsKey(EventLogUploaderOptions.EAP_OPTION);
      return new EventLogExternalApplicationInfo(
        url, productCode, productVersion, userAgent, isInternal, isTest, isEAP, extraHeaders, logger, eventLogger
      );
    }
    return null;
  }

  private static List<SendConfiguration> parseSendConfigurations(@NotNull Map<String, String> options) {
    String recorder = options.get(EventLogUploaderOptions.RECORDERS_OPTION);
    if (recorder == null) {
      return Collections.emptyList();
    }

    String[] recorderIds = recorder.split(";");
    List<SendConfiguration> configurations = new ArrayList<>();
    for (String recorderId : recorderIds) {
      configurations.add(new SendConfiguration(recorderId, options));
    }
    return configurations;
  }

  private static boolean waitForIde(DataCollectorDebugLogger logger, Map<String, String> options, int maxAttempts) {
    String ideToken = options.get(EventLogUploaderOptions.IDE_TOKEN);
    if (ideToken == null) {
      return true;
    }

    logger.info("IDE token file: " + ideToken);
    File token = new File(ideToken);
    try {
      int attempt = 0;
      while (attempt < maxAttempts && token.exists()) {
        logger.info("Waiting for " + WAIT_FOR_IDE_MS + "ms for IDE to close. Attempt #" + attempt);
        //noinspection BusyWait
        Thread.sleep(WAIT_FOR_IDE_MS);
        attempt++;
      }
    }
    catch (InterruptedException e) {
      // ignore
    }
    return !token.exists();
  }

  private static class SendConfiguration {
    @NotNull
    protected final String recorderId;

    @Nullable
    protected final DeviceConfiguration deviceConfig;

    @Nullable
    protected final EventLogRecorderConfig recorderConfig;


    private SendConfiguration(@NotNull String recorder, @NotNull Map<String, String> options) {
      recorderId = recorder;
      deviceConfig = newDeviceConfig(recorder, options);
      recorderConfig = newRecorderConfig(recorder, options);
    }

    @Nullable
    protected DeviceConfiguration newDeviceConfig(@NotNull String recorder, @NotNull Map<String, String> options) {
      String recorderLowerCase = recorder.toLowerCase(Locale.ENGLISH);
      try {
        String bucketOption = options.get(EventLogUploaderOptions.BUCKET_OPTION + recorderLowerCase);
        String deviceOption = options.get(EventLogUploaderOptions.DEVICE_OPTION + recorderLowerCase);
        String machineIdOption = options.get(EventLogUploaderOptions.MACHINE_ID_OPTION + recorderLowerCase);
        String idRevisionOption = options.get(EventLogUploaderOptions.ID_REVISION_OPTION + recorderLowerCase);
        int bucketInt = bucketOption != null ? Integer.parseInt(bucketOption) : -1;
        int idRevision = idRevisionOption != null ? Integer.parseInt(idRevisionOption) : -1;
        if (deviceOption != null && bucketInt >= 0 && bucketInt < 256 && machineIdOption != null && idRevision >= 0) {
          return new DeviceConfiguration(deviceOption, bucketInt, new MachineId(machineIdOption, idRevision));
        }
      }
      catch (NumberFormatException e) {
        // ignore
      }
      return null;
    }

    @Nullable
    protected EventLogRecorderConfig newRecorderConfig(@NotNull String recorder, @NotNull Map<String, String> options) {
      String logs = options.get(EventLogUploaderOptions.LOGS_OPTION + recorder.toLowerCase(Locale.ENGLISH));
      if (logs != null) {
        List<String> files = split(logs, File.pathSeparatorChar);
        return new EventLogExternalRecorderConfig(recorder, files);
      }
      return null;
    }
  }
}
