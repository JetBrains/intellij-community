// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.function.Consumer

object FUCollectorTestCase {
  fun collectLogEvents(parentDisposable: Disposable,
                       action: () -> Unit): List<LogEvent> {
    return collectLogEvents("FUS", parentDisposable, action)
  }

  @JvmStatic
  fun collectProjectStateCollectorEvents(collectorClass: Class<out ProjectUsagesCollector>, project: Project): Set<MetricEvent> {
    val collector: ProjectUsagesCollector = collectorClass.getConstructor().newInstance()
    return runBlockingMaybeCancellable {
      collector.collect(project)
    }
  }

  fun collectApplicationStateCollectorEvents(collectorClass: Class<out ApplicationUsagesCollector>): Set<MetricEvent> {
    val collector: ApplicationUsagesCollector = collectorClass.getConstructor().newInstance()
    return collector.getMetrics()
  }

  fun collectLogEvents(recorder: String,
                       parentDisposable: Disposable,
                       action: () -> Unit): List<LogEvent> = collectLogEvents(recorder, parentDisposable, null, action)

  fun listenForEvents(recorder: String,
                      parentDisposable: Disposable,
                      listener: Consumer<LogEvent>,
                      action: () -> Unit) {
    collectLogEvents(recorder, parentDisposable, listener, action)
    return
  }

  fun collectLogEvents(recorder: String,
                       parentDisposable: Disposable,
                       listener: Consumer<LogEvent>?,
                       action: () -> Unit): List<LogEvent> {
    val mockLoggerProvider = TestStatisticsEventLoggerProvider(recorder)
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(listOf(mockLoggerProvider), parentDisposable, true)
    mockLoggerProvider.logger.eventListener = listener
    action()
    return mockLoggerProvider.getLoggedEvents()
  }

  /**
   * Allows collecting [LogEvent] for multiple recorders simultaneously.
   */
  fun collectLogEvents(listenerPerRecorder: Map<String, Consumer<LogEvent>?>,
                       parentDisposable: Disposable,
                       action: () -> Unit): Map<String, List<LogEvent>> {
    val mockProviderByRecorder: Map<String, TestStatisticsEventLoggerProvider> = listenerPerRecorder.entries.associate { (recorder, listener) ->
      val mockLoggerProvider = TestStatisticsEventLoggerProvider(recorder)
      mockLoggerProvider.logger.eventListener = listener
      recorder to mockLoggerProvider
    }
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(mockProviderByRecorder.values.toList(), parentDisposable, true)
    action()
    return mockProviderByRecorder.mapValues { it.value.getLoggedEvents() }
  }
}
