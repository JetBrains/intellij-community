// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface EventLogSendListener {
  void onLogsSend(@NotNull List<String> successfullySentFiles,
                  @NotNull List<Integer> errors,
                  int totalLocalFiles);
}
