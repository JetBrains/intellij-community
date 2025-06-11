// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.jetbrains.fus.reporting.model.http.StatsConnectionSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface EventLogApplicationInfo {
  @NotNull
  String getRegionalCode();

  @NotNull
  String getProductCode();

  @NotNull
  String getProductVersion();

  int getBaselineVersion();

  @NotNull
  StatsConnectionSettings getConnectionSettings();

  boolean isInternal();

  /**
   *
   * Defines if fus test config url should be used
   */
  boolean isTestConfig();

  /**
   * Defines if staging metadata endpoint should be used to report events
   */
  boolean isTestSendEndpoint();

  boolean isEAP();

  @NotNull
  DataCollectorDebugLogger getLogger();

  @NotNull
  DataCollectorSystemEventLogger getEventLogger();
}
