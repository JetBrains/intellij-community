// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.EventLogRecorderConfig;
import com.intellij.internal.statistic.eventLog.FilesToSendProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EventLogExternalRecorderConfig implements EventLogRecorderConfig {
  private final String myRecorderId;
  private final String myTemplateUrl;
  private final FilesToSendProvider myFilesProvider;

  public EventLogExternalRecorderConfig(@NotNull String recorderId, @NotNull String templateUrl, @NotNull List<String> logs) {
    myRecorderId = recorderId;
    myTemplateUrl = templateUrl;
    myFilesProvider = new EventLogFileListProvider(logs);
  }

  @NotNull
  @Override
  public String getRecorderId() {
    return myRecorderId;
  }

  @NotNull
  @Override
  public String getTemplateUrl() {
    return myTemplateUrl;
  }

  @Override
  public boolean isSendEnabled() {
    return true;
  }

  @NotNull
  @Override
  public FilesToSendProvider getFilesToSendProvider() {
    return myFilesProvider;
  }
}
