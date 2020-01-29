// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalApplicationInfo;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalRecorderConfig;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class EventLogUploader {
  public static void main(String[] args) {
    execute(args);
  }

  private static void execute(String[] args) {
    DataCollectorDebugLogger logger = new ExternalDataCollectorLogger();
    logger.info("Process started with '" + String.join(" ", args) + "'");

    if (args.length == 0) {
      logger.warn("No arguments were found");
      return;
    }

    Map<String, String> options = EventLogUploaderCliParser.parseOptions(args);
    DeviceConfiguration device = newDeviceConfig(options);
    if (device == null) {
      logger.warn("Failed creating device config from arguments");
      return;
    }

    EventLogRecorderConfig recorder = newRecorderConfig(options);
    if (recorder == null) {
      logger.warn("Failed creating recorder config from arguments");
      return;
    }

    EventLogApplicationInfo appInfo = newApplicationInfo(options, logger);
    if (appInfo == null) {
      logger.warn("Failed creating application info from arguments");
      return;
    }

    logger.info("Start uploading...");
    logger.info("{url:" + appInfo.getTemplateUrl() + ", product:" + appInfo.getProductCode() + ", internal:" + appInfo.isInternal() + ", isTest:" + appInfo.isTest() + "}");
    logger.info("{recorder:" + recorder.getRecorderId() + ", root:" + recorder.getLogFilesProvider().getLogFilesDir() + "}");
    logger.info("{device:" + device.getDeviceId() + ", bucket:" + device.getBucket() + "}");
    try {
      //TODO: save the number of uploaded files and log it during the next IDE session
      EventLogStatisticsService service = new EventLogStatisticsService(device, recorder, appInfo, null);
      StatisticsResult result = service.send();
      if (logger.isTraceEnabled()) {
        logger.trace("Uploading finished with " + result.getCode().name());
        logger.trace(result.getDescription());
      }
    }
    catch (Exception e) {
      logger.warn("Failed sending files: " + e.getMessage());
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
      String dir = options.get(EventLogUploaderOptions.DIRECTORY_OPTION);
      Path path = dir != null ? Paths.get(dir) : null;
      if (path != null && path.toFile().exists()) {
        return new EventLogExternalRecorderConfig(recorder, path.toString());
      }
    }
    return null;
  }

  @Nullable
  private static EventLogApplicationInfo newApplicationInfo(Map<String, String> options, DataCollectorDebugLogger logger) {
    String url = options.get(EventLogUploaderOptions.URL_OPTION);
    String productCode = options.get(EventLogUploaderOptions.PRODUCT_OPTION);
    if (url != null && productCode != null) {
      boolean isInternal = options.containsKey(EventLogUploaderOptions.INTERNAL_OPTION);
      boolean isTest = options.containsKey(EventLogUploaderOptions.TEST_OPTION);
      return new EventLogExternalApplicationInfo(url, productCode, isInternal, isTest, logger);
    }
    return null;
  }
}
