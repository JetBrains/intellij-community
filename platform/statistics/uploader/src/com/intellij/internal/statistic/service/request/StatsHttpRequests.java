// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.request;

import org.jetbrains.annotations.NotNull;

public class StatsHttpRequests {
  public static StatsRequestBuilder request(@NotNull String url, @NotNull String userAgent) {
    return new StatsRequestBuilder("GET", url, userAgent);
  }

  public static StatsRequestBuilder head(@NotNull String url, @NotNull String userAgent) {
    return new StatsRequestBuilder("HEAD", url, userAgent);
  }

  public static StatsRequestBuilder post(@NotNull String url, @NotNull String userAgent) {
    return new StatsRequestBuilder("POST", url, userAgent);
  }
}
