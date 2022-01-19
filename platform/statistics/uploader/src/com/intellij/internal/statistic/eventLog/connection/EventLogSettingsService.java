// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection;

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@ApiStatus.Internal
public interface EventLogSettingsService {

  @Nullable
  String getServiceUrl();

  @Nullable
  String getDictionaryServiceUrl();

  /**
   * @return true if it's possible to load settings from server, false otherwise
   */
  boolean isSettingsReachable();

  /**
   * @return true if send is enable for a current IDE version (i.e. send configuration exists), false otherwise
   */
  boolean isSendEnabled();

  @NotNull
  LogEventFilter getBaseEventFilter();

  @NotNull
  LogEventFilter getEventFilter(@NotNull LogEventFilter base, @NotNull EventLogBuildType type);

  @NotNull
  EventLogApplicationInfo getApplicationInfo();

  @NotNull
  public Map<String, String> getOptions();
}
