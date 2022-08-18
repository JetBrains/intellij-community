// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link EventLogInternalSendConfig} because it contains both information about recorder and device
 * Kept for compatibility with TBE.
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public class EventLogInternalRecorderConfig implements EventLogRecorderConfig {
  private final String myRecorderId;
  private final boolean myFilterActiveFile;

  public EventLogInternalRecorderConfig(@NotNull String recorderId, boolean filterActiveFile) {
    myRecorderId = recorderId;
    myFilterActiveFile = filterActiveFile;
  }

  public EventLogInternalRecorderConfig(@NotNull String recorderId) {
    this(recorderId, true);
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

  @NotNull
  @Override
  public FilesToSendProvider getFilesToSendProvider() {
    int maxFilesToSend = EventLogConfiguration.getInstance().getOrCreate(myRecorderId).getMaxFilesToSend();
    EventLogFilesProvider logFilesProvider = StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).getLogFilesProvider();
    return new DefaultFilesToSendProvider(logFilesProvider, maxFilesToSend, myFilterActiveFile);
  }
}
