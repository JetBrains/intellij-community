// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger

object FUCounterCollectorTestCase {
  fun collectLogEvents(action: () -> Unit): List<LogEvent> {
    val oldLogger = FeatureUsageLogger.loggerProvider
    try {
      val mockLoggerProvider = TestStatisticsEventLoggerProvider()
      FeatureUsageLogger.loggerProvider = mockLoggerProvider
      action()
      return mockLoggerProvider.getLoggedEvents()
    }
    finally {
      FeatureUsageLogger.loggerProvider = oldLogger
    }
  }
}