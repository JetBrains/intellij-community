// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.config.bean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class EventLogConfigVersions {
  @Nullable
  public EventLogMajorVersion majorBuildVersionBorders;

  @Nullable
  public Map<String, String> endpoints;

  @Nullable
  public List<EventLogConfigFilterCondition> releaseFilters;

  @NotNull
  public Map<String, String> getEndpoints() {
    return endpoints != null ? endpoints : Collections.emptyMap();
  }

  @NotNull
  public List<EventLogConfigFilterCondition> getFilters() {
    return releaseFilters != null ? releaseFilters : Collections.emptyList();
  }

  public static class EventLogConfigFilterCondition {
    @Nullable
    public String releaseType;

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
