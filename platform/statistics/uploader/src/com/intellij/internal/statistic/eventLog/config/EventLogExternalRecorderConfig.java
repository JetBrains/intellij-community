// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.EventLogFilesProvider;
import com.intellij.internal.statistic.eventLog.EventLogRecorderConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EventLogExternalRecorderConfig implements EventLogRecorderConfig {
  private final String myRecorderId;
  private final EventLogFilesProvider myFilesProvider;

  public EventLogExternalRecorderConfig(@NotNull String recorderId, @NotNull List<String> logs) {
    myRecorderId = recorderId;
    myFilesProvider = new EventLogFileListProvider(logs);
  }

  @NotNull
  @Override
  public String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public boolean isSendEnabled() {
    return true;
  }

  @NotNull
  @Override
  public EventLogFilesProvider getLogFilesProvider() {
    return myFilesProvider;
  }
}
