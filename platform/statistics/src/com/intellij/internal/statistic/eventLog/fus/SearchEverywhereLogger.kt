// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.getEventLogProvider

object SearchEverywhereLogger {
  /**
   * [com.intellij.internal.statistic.eventLog.fus.SearchEverywhereEventLoggerProvider]
   */
  internal val loggerProvider = getEventLogProvider("MLSE")
  internal val group = EventLogGroup("mlse.log", 1)

  @JvmStatic
  fun log(eventId: String, data: Map<String, Any>) {
    loggerProvider.logger.logAsync(group, eventId, data, false)
  }
}