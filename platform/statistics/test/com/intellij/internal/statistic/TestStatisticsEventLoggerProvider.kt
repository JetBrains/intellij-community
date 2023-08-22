// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.jetbrains.fus.reporting.model.lion3.LogEvent

class TestStatisticsEventLoggerProvider(recorder: String) : StatisticsEventLoggerProvider(recorder, 1, DEFAULT_SEND_FREQUENCY_MS, DEFAULT_MAX_FILE_SIZE_BYTES) {
  override val logger: TestStatisticsEventLogger = TestStatisticsEventLogger()

  override fun isRecordEnabled(): Boolean = true

  override fun isSendEnabled(): Boolean = false

  fun getLoggedEvents(): List<LogEvent> = logger.logged
}

