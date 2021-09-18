// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.jetbrains.fus.reporting.model.lion3.LogEvent

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