// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config.bean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class EventLogConfigVersions {
  public @Nullable EventLogMajorVersionBorders majorBuildVersionBorders;

  public @Nullable Map<String, String> endpoints;

  public @Nullable Map<String, String> options;

  public @Nullable List<EventLogConfigFilterCondition> releaseFilters;

  public @NotNull Map<String, String> getEndpoints() {
    return endpoints != null ? endpoints : Collections.emptyMap();
  }

  public @NotNull Map<String, String> getOptions() {
    return options != null ? options : Collections.emptyMap();
  }

  public @NotNull List<EventLogConfigFilterCondition> getFilters() {
    return releaseFilters != null ? releaseFilters : Collections.emptyList();
  }

  public static class EventLogConfigFilterCondition {
    public @Nullable String releaseType;

    public int from = 0;

    public int to = Integer.MAX_VALUE;

    public EventLogBucketRange getBucketRange() {
      return isValid() ? new EventLogBucketRange(from, to) : null;
    }

    private boolean isValid() {
      return from != 0 || to != Integer.MAX_VALUE;
    }
  }
}
