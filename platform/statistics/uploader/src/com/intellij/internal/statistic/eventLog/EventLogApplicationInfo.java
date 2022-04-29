// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface EventLogApplicationInfo {
  /**
   * @deprecated Use "EventLogExternalRecorderConfig#getTemplateUrl()" because url depends on the recorder.
   * <br/>
   * Temporary keep this method for backward compatibility with TBE.
   */
  @NotNull
  @Deprecated(forRemoval = true)
  String getTemplateUrl();

  @NotNull
  String getProductCode();

  @NotNull
  String getProductVersion();

  @NotNull
  EventLogConnectionSettings getConnectionSettings();

  boolean isInternal();

  boolean isTest();

  boolean isEAP();

  @NotNull
  DataCollectorDebugLogger getLogger();

  @NotNull
  DataCollectorSystemEventLogger getEventLogger();
}
