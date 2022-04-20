// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
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

  fun collectStateCollectorEvents(collectorClass: Class<out ProjectUsagesCollector>, project: Project): Set<MetricEvent> {
    val collector: ProjectUsagesCollector = collectorClass.getConstructor().newInstance()
    val method = collectorClass.getMethod("getMetrics", Project::class.java)
    return method.invoke(collector, project) as Set<MetricEvent>
  }
}