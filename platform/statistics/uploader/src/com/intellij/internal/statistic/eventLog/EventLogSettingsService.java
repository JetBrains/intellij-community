// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.filters.LogEventFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}
