// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.EventLogFile;
import com.intellij.internal.statistic.eventLog.FilesToSendProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class EventLogFileListProvider implements FilesToSendProvider {
  private final List<EventLogFile> myFiles;

  public EventLogFileListProvider(@NotNull List<String> paths) {
    myFiles = paths.stream().
      map(path -> new File(path)).
      filter(file -> file.exists()).
      map(file -> new EventLogFile(file)).
      collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<EventLogFile> getFilesToSend() {
    return myFiles;
  }
}
