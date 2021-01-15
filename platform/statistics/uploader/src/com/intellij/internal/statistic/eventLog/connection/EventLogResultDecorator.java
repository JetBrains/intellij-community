// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;

import com.intellij.internal.statistic.eventLog.LogEventRecordRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface EventLogResultDecorator {
  default void onLogsLoaded(int localFiles) {}

  void onSucceed(@NotNull LogEventRecordRequest request, @NotNull String content, @NotNull String logPath);

  void onFailed(@Nullable LogEventRecordRequest request, int error, @Nullable String content);

  @NotNull
  StatisticsResult onFinished();
}
