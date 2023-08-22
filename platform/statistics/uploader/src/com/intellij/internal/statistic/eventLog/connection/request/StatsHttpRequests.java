// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection.request;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import org.jetbrains.annotations.NotNull;

public final class StatsHttpRequests {
  public static StatsRequestBuilder request(@NotNull String url, @NotNull EventLogConnectionSettings settings) {
    return new StatsRequestBuilder("GET", url, settings);
  }

  public static StatsRequestBuilder head(@NotNull String url, @NotNull EventLogConnectionSettings settings) {
    return new StatsRequestBuilder("HEAD", url, settings);
  }

  public static StatsRequestBuilder post(@NotNull String url, @NotNull EventLogConnectionSettings settings) {
    return new StatsRequestBuilder("POST", url, settings);
  }
}
