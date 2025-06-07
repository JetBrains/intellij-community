// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import com.intellij.internal.statistic.eventLog.DataCollectorSystemEventLogger;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import com.intellij.internal.statistic.eventLog.EventLogSendConfig;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalApplicationInfo;
import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener;
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult;
import com.intellij.internal.statistic.uploader.events.ExternalEventsLogger;
import com.intellij.internal.statistic.uploader.util.ExtraHTTPHeadersParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
      eventsLogger.logSendingLogsFinished(StatisticsResult.ResultCode.EXCEPTION_OCCURRED);
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
      eventsLogger.logSendingLogsFinished(StatisticsResult.ResultCode.NO_ARGUMENTS);
      return;
    }

    Map<String, String> options = EventLogUploaderCliParser.parseOptions(args);
    EventLogApplicationInfo appInfo = newApplicationInfo(options, logger, eventsLogger);
    if (appInfo == null) {
      logger.warn("Failed creating application info from arguments");
      eventsLogger.logSendingLogsFinished(StatisticsResult.ResultCode.NO_APPLICATION_CONFIG);
      return;
    }

    List<EventLogExternalSendConfig> configs = EventLogExternalSendConfig.parseSendConfigurations(options, (recorder, error) -> {
      logger.warn("[" + recorder + "] Failed creating send config from arguments because " + error.getType().name());
      eventsLogger.logSendingLogsFinished(recorder, error.getType().name());
    });

    if (!waitForIde(logger, options, 20)) {
      logger.warn("Cannot send logs because IDE didn't close during " + (20 * WAIT_FOR_IDE_MS) + "ms");
      eventsLogger.logSendingLogsFinished(StatisticsResult.ResultCode.IDE_NOT_CLOSING);
      return;
    }

    ExecutorService service = Executors.newFixedThreadPool(configs.size());
    for (EventLogExternalSendConfig config : configs) {
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
                                         @NotNull EventLogSendConfig config,
                                         @NotNull DataCollectorDebugLogger logger,
                                         @NotNull ExternalEventsLogger eventsLogger) {
    String recorderId = config.getRecorderId();
    logger.info("[" + recorderId + "] Start uploading...");
    EventLogConnectionSettings connectionSettings = appInfo.getConnectionSettings();
    logger.info("[" + recorderId + "] {"
                + "product:" + appInfo.getProductCode()
                + ", productVersion:" + appInfo.getProductVersion()
                + ", userAgent:" + connectionSettings.getUserAgent()
                + ", url: " + appInfo.getTemplateUrl()
                + ", internal:" + appInfo.isInternal()
                + ", isTestConfig:" + appInfo.isTestConfig()
                + ", isTestSendEndpoint:" + appInfo.isTestSendEndpoint() + "}");

    String logs = config.getFilesToSendProvider().getFilesToSend().stream().
      map(file -> file.getFile().getAbsolutePath()).collect(Collectors.joining(File.pathSeparator));
    logger.info("[" + recorderId + "] {recorder:" + config.getRecorderId() + ", files:" + logs + "}");
    logger.info("[" + recorderId + "] {device:" + config.getDeviceId() + ", bucket:" + config.getBucket() + "}");
    try {
      EventLogStatisticsService service = new EventLogStatisticsService(
        config, appInfo,
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
      eventsLogger.logSendingLogsFinished(recorderId, StatisticsResult.ResultCode.ERROR_ON_SEND);
    }
  }

  private static @Nullable EventLogApplicationInfo newApplicationInfo(Map<String, String> options,
                                                                      DataCollectorDebugLogger logger,
                                                                      DataCollectorSystemEventLogger eventLogger) {
    String productCode = options.get(EventLogUploaderOptions.PRODUCT_OPTION);
    String productVersion = options.get(EventLogUploaderOptions.PRODUCT_VERSION_OPTION);
    String url = options.get(EventLogUploaderOptions.URL_OPTION);
    String userAgent = options.get(EventLogUploaderOptions.USER_AGENT_OPTION);
    String headersString = options.get(EventLogUploaderOptions.EXTRA_HEADERS);
    Map<String, String> extraHeaders = ExtraHTTPHeadersParser.parse(headersString);
    int baselineVersion = Integer.parseInt(options.get(EventLogUploaderOptions.BASELINE_VERSION));
    if (url != null && productCode != null) {
      boolean isInternal = options.containsKey(EventLogUploaderOptions.INTERNAL_OPTION);
      boolean isTestSendEndpoint = options.containsKey(EventLogUploaderOptions.TEST_SEND_ENDPOINT);
      boolean isTestConfig = options.containsKey(EventLogUploaderOptions.TEST_CONFIG);
      boolean isEAP = options.containsKey(EventLogUploaderOptions.EAP_OPTION);
      return new EventLogExternalApplicationInfo(
        url, productCode, productVersion, userAgent, isInternal, isTestConfig, isTestSendEndpoint, isEAP, extraHeaders, logger, eventLogger,
        baselineVersion);
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
