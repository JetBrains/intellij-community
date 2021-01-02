// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.config;

import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class EventLogExternalSendSettings {
  private final Map<String, String> myEndpoints;
  private final Map<EventLogBuildType, EventLogSendConfiguration> myConfigurations;
  private final Map<String, String> myOptions;

  public EventLogExternalSendSettings(@NotNull Map<String, String> endpoints,
                                      @NotNull Map<String, String> options,
                                      @NotNull Map<EventLogBuildType, EventLogSendConfiguration> configurations) {
    myEndpoints = endpoints;
    myOptions = options;
    myConfigurations = configurations;
  }

  public boolean isSendEnabled() {
    return !myConfigurations.isEmpty();
  }

  @Nullable
  public EventLogSendConfiguration getConfiguration(@NotNull EventLogBuildType type) {
    return myConfigurations.get(type);
  }

  @Nullable
  public String getEndpoint(@NotNull String name) {
    return myEndpoints.get(name);
  }

  @Nullable
  public String getOption(@NotNull String name) {
    return myOptions.get(name);
  }
}
