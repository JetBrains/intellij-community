// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.jetbrains.fus.reporting.model.lion3.LogEvent

object FUCounterCollectorTestCase {
  fun collectLogEvents(parentDisposable: Disposable,
                       action: () -> Unit): List<LogEvent> {
    return collectLogEvents("FUS", parentDisposable, action)
  }

  fun collectLogEvents(recorder: String,
                       parentDisposable: Disposable,
                       action: () -> Unit): List<LogEvent> {
    val mockLoggerProvider = TestStatisticsEventLoggerProvider(recorder)
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(listOf(mockLoggerProvider), parentDisposable, true)
    action()
    return mockLoggerProvider.getLoggedEvents()
  }

}