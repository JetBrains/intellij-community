// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.EventLogFile;
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class EventLogFileListProvider implements EventLogFilesProvider {
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
  public List<EventLogFile> getLogFiles() {
    return myFiles;
  }

  @Nullable
  @Override
  public Path getLogFilesDir() {
    return myFiles.isEmpty() ? null : Paths.get(myFiles.get(0).getFile().getParent());
  }
}
