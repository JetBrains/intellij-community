// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.eventLog.DeviceConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogRecorderConfig;
import com.intellij.internal.statistic.eventLog.MachineId;
import com.intellij.internal.statistic.eventLog.config.EventLogExternalRecorderConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.split;

public class SendConfiguration {
  @NotNull
  private final String recorderId;

  @Nullable
  private final DeviceConfiguration deviceConfig;

  @Nullable
  private final EventLogRecorderConfig recorderConfig;


  public SendConfiguration(@NotNull String recorder,
                           @Nullable DeviceConfiguration deviceConfig,
                           @Nullable EventLogRecorderConfig recorderConfig) {
    this.recorderId = recorder;
    this.deviceConfig = deviceConfig;
    this.recorderConfig = recorderConfig;
  }

  @NotNull
  public String getRecorderId() {
    return recorderId;
  }

  @Nullable
  public DeviceConfiguration getDeviceConfig() {
    return deviceConfig;
  }

  @Nullable
  public EventLogRecorderConfig getRecorderConfig() {
    return recorderConfig;
  }

  public static List<SendConfiguration> parseSendConfigurations(@NotNull Map<String, String> options) {
    String recorder = options.get(EventLogUploaderOptions.RECORDERS_OPTION);
    if (recorder == null) {
      return Collections.emptyList();
    }

    String[] recorderIds = recorder.split(";");
    List<SendConfiguration> configurations = new ArrayList<>();
    for (String recorderId : recorderIds) {
      configurations.add(new SendConfiguration(recorderId, newDeviceConfig(recorderId, options), newRecorderConfig(recorderId, options)));
    }
    return configurations;
  }



  @Nullable
  private static DeviceConfiguration newDeviceConfig(@NotNull String recorder, @NotNull Map<String, String> options) {
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
  private static EventLogRecorderConfig newRecorderConfig(@NotNull String recorder, @NotNull Map<String, String> options) {
    String logs = options.get(EventLogUploaderOptions.LOGS_OPTION + recorder.toLowerCase(Locale.ENGLISH));
    if (logs != null) {
      List<String> files = split(logs, File.pathSeparatorChar);
      return new EventLogExternalRecorderConfig(recorder, files);
    }
    return null;
  }
}
