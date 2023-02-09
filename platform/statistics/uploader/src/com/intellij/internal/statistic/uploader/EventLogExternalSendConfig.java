// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.config.StatisticsStringUtil;
import com.intellij.internal.statistic.eventLog.EventLogSendConfig;
import com.intellij.internal.statistic.eventLog.FilesToSendProvider;
import com.intellij.internal.statistic.eventLog.MachineId;
import com.intellij.internal.statistic.eventLog.config.EventLogFileListProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class EventLogExternalSendConfig implements EventLogSendConfig {
  @NotNull
  private final String myRecorderId;

  private final String myDeviceId;

  private final int myBucket;

  private final MachineId myMachineId;

  private final FilesToSendProvider myFilesProvider;

  private final boolean myIsSendEnabled;


  public EventLogExternalSendConfig(@NotNull String recorder,
                                    @NotNull String deviceId,
                                    int bucket,
                                    @NotNull MachineId machineId,
                                    @NotNull List<String> logs,
                                    boolean isSendEnabled) {
    myRecorderId = recorder;
    myDeviceId = deviceId;
    myBucket = bucket;
    myMachineId = machineId;
    myFilesProvider = new EventLogFileListProvider(logs);
    myIsSendEnabled = isSendEnabled;
  }

  @Override
  public @NotNull String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public @NotNull String getDeviceId() {
    return myDeviceId;
  }

  @Override
  public int getBucket() {
    return myBucket;
  }

  @Override
  public @NotNull MachineId getMachineId() {
    return myMachineId;
  }

  @Override
  public boolean isSendEnabled() {
    return myIsSendEnabled;
  }

  @NotNull
  @Override
  public FilesToSendProvider getFilesToSendProvider() {
    return myFilesProvider;
  }

  public static List<EventLogExternalSendConfig> parseSendConfigurations(@NotNull Map<String, String> options,
                                                                         @NotNull ParseConfigurationExceptionHandler errorHandler) {
    String recorder = options.get(EventLogUploaderOptions.RECORDERS_OPTION);
    if (recorder == null) {
      return Collections.emptyList();
    }

    String[] recorderIds = recorder.split(";");
    List<EventLogExternalSendConfig> configurations = new ArrayList<>();
    for (String recorderId : recorderIds) {
      try {
        configurations.add(parseSendConfiguration(recorderId, options));
      }
      catch (ParseSendConfigurationException e) {
        errorHandler.handle(recorderId, e);
      }
    }
    return configurations;
  }

  private static EventLogExternalSendConfig parseSendConfiguration(@NotNull String recorderId, @NotNull Map<String, String> options)
    throws ParseSendConfigurationException {
    String recorderLowerCase = recorderId.toLowerCase(Locale.ENGLISH);

    String deviceOption = options.get(EventLogUploaderOptions.DEVICE_OPTION + recorderLowerCase);
    if (deviceOption == null) {
      throw new ParseSendConfigurationException(ParseErrorType.NO_DEVICE_ID);
    }
    String machineIdOption = options.get(EventLogUploaderOptions.MACHINE_ID_OPTION + recorderLowerCase);
    if (machineIdOption == null) {
      throw new ParseSendConfigurationException(ParseErrorType.NO_MACHINE_ID);
    }

    int bucket = getIntOption(EventLogUploaderOptions.BUCKET_OPTION + recorderLowerCase, options);
    if (bucket < 0 || bucket >= 256) {
      throw new ParseSendConfigurationException(ParseErrorType.INVALID_BUCKET);
    }

    int idRevisionOption = getIntOption(EventLogUploaderOptions.ID_REVISION_OPTION + recorderLowerCase, options);
    if (idRevisionOption < 0) {
      throw new ParseSendConfigurationException(ParseErrorType.INVALID_REVISION);
    }

    String logs = options.get(EventLogUploaderOptions.LOGS_OPTION + recorderLowerCase);
    if (logs == null) {
      throw new ParseSendConfigurationException(ParseErrorType.NO_LOG_FILES);
    }

    List<String> files = StatisticsStringUtil.split(logs, File.pathSeparatorChar);
    return new EventLogExternalSendConfig(recorderId, deviceOption, bucket, new MachineId(machineIdOption, idRevisionOption), files, true);
  }

  private static int getIntOption(@NotNull String name, @NotNull Map<String, String> options) {
    try {
      String option = options.get(name);
      return option != null ? Integer.parseInt(option) : -1;
    }
    catch (NumberFormatException e) {
      // ignore
    }
    return -1;
  }

  public static class ParseSendConfigurationException extends Exception {
    private final ParseErrorType myType;

    public ParseSendConfigurationException(@NotNull ParseErrorType type) {
      myType = type;
    }

    public @NotNull ParseErrorType getType() {
      return myType;
    }
  }

  public enum ParseErrorType {
    NO_DEVICE_ID, NO_MACHINE_ID, INVALID_BUCKET, INVALID_REVISION, NO_LOG_FILES
  }

  public interface ParseConfigurationExceptionHandler {
    void handle(@NotNull String recorderId, @NotNull ParseSendConfigurationException exception);
  }
}
