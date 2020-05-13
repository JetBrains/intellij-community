// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider

class TestStatisticsEventLoggerProvider : StatisticsEventLoggerProvider("FUS", 1) {
  override val logger: TestStatisticsEventLogger = TestStatisticsEventLogger()

  override fun isRecordEnabled(): Boolean = true

  override fun isSendEnabled(): Boolean = false

  fun getLoggedEvents(): List<LogEvent> = logger.logged
}

