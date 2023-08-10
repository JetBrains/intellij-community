// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EventLogInternalSendConfig implements EventLogSendConfig {
  private final String myRecorderId;

  private final String myDeviceId;

  private final int myBucket;

  private final MachineId myMachineId;

  private final boolean myFilterActiveFile;

  private EventLogInternalSendConfig(@NotNull String recorderId, @NotNull EventLogRecorderConfiguration config, boolean filterActiveFile) {
    myRecorderId = recorderId;
    myDeviceId = config.getDeviceId();
    myBucket = config.getBucket();
    myMachineId = config.getMachineId();
    myFilterActiveFile = filterActiveFile;
  }

  @NotNull
  public static EventLogInternalSendConfig createByRecorder(@NotNull String recorderId, boolean filterActiveFile) {
    EventLogRecorderConfiguration config = EventLogConfiguration.getInstance().getOrCreate(recorderId);
    return new EventLogInternalSendConfig(recorderId, config, filterActiveFile);
  }

  @NotNull
  @Override
  public String getDeviceId() {
    return myDeviceId;
  }

  @Override
  public int getBucket() {
    return myBucket;
  }

  @NotNull
  @Override
  public MachineId getMachineId() {
    return myMachineId;
  }

  @NotNull
  @Override
  public String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public boolean isSendEnabled() {
    return StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).isSendEnabled();
  }

  @Override
  public boolean isEscapingEnabled() {
    return StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).isCharsEscapingRequired();
  }

  @NotNull
  @Override
  public FilesToSendProvider getFilesToSendProvider() {
    int maxFilesToSend = EventLogConfiguration.getInstance().getOrCreate(myRecorderId).getMaxFilesToSend();
    EventLogFilesProvider logFilesProvider = StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).getLogFilesProvider();
    return new DefaultFilesToSendProvider(logFilesProvider, maxFilesToSend, myFilterActiveFile);
  }
}
