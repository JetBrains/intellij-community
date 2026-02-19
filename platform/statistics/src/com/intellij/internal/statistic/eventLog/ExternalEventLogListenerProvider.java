// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;



@ApiStatus.Internal
public interface ExternalEventLogListenerProvider {

  /**
   * Enables statistics logging ({@link StatisticsEventLoggerProviderExt#isLoggingAlwaysActive()}) independently of
   * recording to file ({@link StatisticsEventLoggerProvider#isRecordEnabled()}) for <b>supported</b> loggers.
   * <br/>
   * Logger must implement {@link StatisticsEventLoggerProviderExt}.
   * <br/>
   * Is not affected by {@link ExternalEventLogSettings#forceDisableCollectionConsent()}
   *
   * @return true if statistics collection must be force-enabled by supported logger
   */
  default boolean forceLoggingAlwaysEnabled() {
    return false;
  }

  /**
   * Provides implementations of {@link StatisticsEventLogListener} to be used in {@link EventLogListenersManager}
   * <br/>
   * This method will be called only once per recorder on IDE start or plugin loading (for dynamic plugins)
   *
   * @param recorderId of a recorder which logs will trigger provided listener
   * */
  default @Nullable StatisticsEventLogListener getEventLogListener(@NotNull String recorderId) {
    return null;
  }
}
