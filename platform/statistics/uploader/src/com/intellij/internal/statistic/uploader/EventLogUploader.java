// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalApplicationInfo;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalRecorderConfig;
import com.intellij.internal.statistic.uploader.events.ExternalEventsLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.StatisticsStringUtil.split;

public final class EventLogUploader {
  private static final int WAIT_FOR_IDE_MS = 2000;

  public static void main(String[] args) {
    execute(args);
  }

  private static void execute(String[] args) {
    ExternalEventsLogger eventsLogger = new ExternalEventsLogger();
    DataCollectorDebugLogger logger = new ExternalDataCollectorLogger();
    logger.info("Process started with '" + String.join(" ", args) + "'");

    eventsLogger.logSendingLogsStarted();
    if (args.length == 0) {
      logger.warn("No arguments were found");
      eventsLogger.logSendingLogsFinished("NO_ARGUMENTS");
      return;
    }

    Map<String, String> options = EventLogUploaderCliParser.parseOptions(args);
    DeviceConfiguration device = newDeviceConfig(options);
    if (device == null) {
      logger.warn("Failed creating device config from arguments");
      eventsLogger.logSendingLogsFinished("NO_DEVICE_CONFIG");
      return;
    }

    EventLogRecorderConfig recorder = newRecorderConfig(options);
    if (recorder == null) {
      logger.warn("Failed creating recorder config from arguments");
      eventsLogger.logSendingLogsFinished("NO_RECORDER_CONFIG");
      return;
    }

    EventLogApplicationInfo appInfo = newApplicationInfo(options, logger, eventsLogger);
    if (appInfo == null) {
      logger.warn("Failed creating application info from arguments");
      eventsLogger.logSendingLogsFinished("NO_APPLICATION_CONFIG");
      return;
    }

    if (!waitForIde(logger, options, 20)) {
      logger.warn("Cannot send logs because IDE didn't close during " + (20 * WAIT_FOR_IDE_MS) + "ms");
      eventsLogger.logSendingLogsFinished("IDE_NOT_CLOSING");
      return;
    }

    logger.info("Start uploading...");
    logger.info("{url:" + appInfo.getTemplateUrl() + ", product:" + appInfo.getProductCode() + ", userAgent:" + appInfo.getUserAgent() +
                ", internal:" + appInfo.isInternal() + ", isTest:" + appInfo.isTest() + "}");
    String logs = recorder.getLogFilesProvider().getLogFiles().stream().
      map(file -> file.getFile().getAbsolutePath()).collect(Collectors.joining(File.pathSeparator));

    logger.info("{recorder:" + recorder.getRecorderId() + ", files:" + logs + "}");
    logger.info("{device:" + device.getDeviceId() + ", bucket:" + device.getBucket() + "}");
    try {
      EventLogStatisticsService service = new EventLogStatisticsService(device, recorder, appInfo, new EventLogSendListener() {
        @Override
        public void onLogsSend(@NotNull List<String> successfullySentFiles, int failed, int totalLocalFiles) {
          eventsLogger.logSendingLogsSucceed(successfullySentFiles, failed, totalLocalFiles);
        }
      });

      StatisticsResult result = service.send();
      eventsLogger.logSendingLogsFinished(result.getCode());
      if (logger.isTraceEnabled()) {
        logger.trace("Uploading finished with " + result.getCode().name());
        logger.trace(result.getDescription());
      }
    }
    catch (Exception e) {
      logger.warn("Failed sending files: " + e.getMessage());
      eventsLogger.logSendingLogsFinished("ERROR_ON_SEND");
    }
  }

  @Nullable
  private static DeviceConfiguration newDeviceConfig(Map<String, String> options) {
    try {
      String bucketOption = options.get(EventLogUploaderOptions.BUCKET_OPTION);
      String deviceOption = options.get(EventLogUploaderOptions.DEVICE_OPTION);
      int bucketInt = bucketOption != null ? Integer.parseInt(bucketOption) : -1;
      if (deviceOption != null && bucketInt >= 0 && bucketInt < 256) {
        return new DeviceConfiguration(deviceOption, bucketInt);
      }
    }
    catch (NumberFormatException e) {
      // ignore
    }
    return null;
  }

  @Nullable
  private static EventLogRecorderConfig newRecorderConfig(Map<String, String> options) {
    String recorder = options.get(EventLogUploaderOptions.RECORDER_OPTION);
    if (recorder != null) {
      String logs = options.get(EventLogUploaderOptions.LOGS_OPTION);
      if (logs != null) {
        List<String> files = split(logs, File.pathSeparatorChar);
        return new EventLogExternalRecorderConfig(recorder, files);
      }
    }
    return null;
  }

  @Nullable
  private static EventLogApplicationInfo newApplicationInfo(Map<String, String> options,
                                                            DataCollectorDebugLogger logger,
                                                            DataCollectorSystemEventLogger eventLogger) {
    String url = options.get(EventLogUploaderOptions.URL_OPTION);
    String productCode = options.get(EventLogUploaderOptions.PRODUCT_OPTION);
    String productVersion = options.get(EventLogUploaderOptions.PRODUCT_VERSION_OPTION);
    String userAgent = options.get(EventLogUploaderOptions.USER_AGENT_OPTION);
    if (url != null && productCode != null) {
      boolean isInternal = options.containsKey(EventLogUploaderOptions.INTERNAL_OPTION);
      boolean isTest = options.containsKey(EventLogUploaderOptions.TEST_OPTION);
      boolean isEAP = options.containsKey(EventLogUploaderOptions.EAP_OPTION);
      return new EventLogExternalApplicationInfo(
        url, productCode, productVersion, userAgent, isInternal, isTest, isEAP, logger, eventLogger
      );
    }
    return null;
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
}
