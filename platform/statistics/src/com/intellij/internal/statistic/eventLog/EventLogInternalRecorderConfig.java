// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EventLogInternalRecorderConfig implements EventLogRecorderConfig {
  private final String myRecorderId;

  public EventLogInternalRecorderConfig(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @NotNull
  @Override
  public String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public boolean isSendEnabled() {
    return StatisticsEventLoggerKt.getEventLogProvider(myRecorderId).isSendEnabled();
  }

  @NotNull
  @Override
  public EventLogFilesProvider getLogFilesProvider() {
    return StatisticsEventLoggerKt.getEventLogProvider(myRecorderId).getLogFilesProvider();
  }
}
