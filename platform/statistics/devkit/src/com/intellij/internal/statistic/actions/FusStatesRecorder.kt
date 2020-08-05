// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.EventLogNotificationService
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object FusStatesRecorder {
  private val log = logger<FusStatesRecorder>()
  private val statesLogger = FUStateUsagesLogger()
  private val state = ConcurrentLinkedQueue<LogEvent>()
  private var isRecordingInProgress = AtomicBoolean(false)
  private val lock = Any()

  fun recordStateAndWait(project: Project, indicator: ProgressIndicator): List<LogEvent>? {
    synchronized(lock) {
      state.clear()
      isRecordingInProgress.getAndSet(true)
      val subscriber: (LogEvent) -> Unit = { this.recordEvent(it) }
      val recorderId = StatisticsDevKitUtil.DEFAULT_RECORDER
      EventLogNotificationService.subscribe(subscriber, recorderId)
      try {
        val logApplicationStatesFuture = statesLogger.logApplicationStates()
        val logProjectStatesFuture = statesLogger.logProjectStates(project, indicator)
        val settingsFuture = CompletableFuture.allOf(
          *FeatureUsageStateEventTracker.EP_NAME.extensions.map { it.reportNow() }.toTypedArray())

        CompletableFuture.allOf(logApplicationStatesFuture, logProjectStatesFuture, settingsFuture)
          .thenCompose { FeatureUsageLogger.flush() }
          .get(10, TimeUnit.SECONDS)
      }
      catch (e: Exception) {
        log.warn("Failed recording state collectors to log", e)
        return null
      }
      finally {
        EventLogNotificationService.unsubscribe(subscriber, recorderId)
        isRecordingInProgress.getAndSet(false)
      }
      return state.toList()
    }
  }

  private fun recordEvent(logEvent: LogEvent) {
    if (logEvent.event.state) {
      state.add(logEvent)
    }
  }

  fun getCurrentState(): List<LogEvent> {
    return state.toList()
  }

  fun isRecordingInProgress(): Boolean = isRecordingInProgress.get()

  fun isComparisonAvailable(): Boolean = !isRecordingInProgress.get() && state.isNotEmpty()
}