// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.fus.reporting.model.lion3.LogEvent
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
      val subscriber = object : StatisticsEventLogListener {
        override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
          recordEvent(validatedEvent)
        }
      }
      val recorderId = StatisticsDevKitUtil.DEFAULT_RECORDER
      service<EventLogListenersManager>().subscribe(subscriber, recorderId)
      try {
        val logApplicationStatesFuture = statesLogger.logApplicationStates()
        val logProjectStatesFuture = statesLogger.logProjectStates(project, indicator)
        val settingsFuture = CompletableFuture.allOf(
          *FeatureUsageStateEventTracker.EP_NAME.extensions.map { it.reportNow() }.toTypedArray())

        CompletableFuture.allOf(logApplicationStatesFuture, logProjectStatesFuture, settingsFuture)
          .thenCompose { FeatureUsageLogger.flush() }
          .get(30, TimeUnit.SECONDS)
      }
      catch (e: Exception) {
        log.warn("Failed recording state collectors to log", e)
        return null
      }
      finally {
        service<EventLogListenersManager>().unsubscribe(subscriber, recorderId)
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