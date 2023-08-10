// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsEventLoggerProviderTest {
  @Test
  fun testParseLogFileSize() {
    assertEquals(200 * 1024, StatisticsEventLoggerProvider.parseFileSize("200KB"))
    assertEquals(2 * 1024 * 1024, StatisticsEventLoggerProvider.parseFileSize("2MB"))
    assertEquals(StatisticsEventLoggerProvider.DEFAULT_MAX_FILE_SIZE_BYTES, StatisticsEventLoggerProvider.parseFileSize("MB"))
    assertEquals(StatisticsEventLoggerProvider.DEFAULT_MAX_FILE_SIZE_BYTES, StatisticsEventLoggerProvider.parseFileSize("15"))
  }
}