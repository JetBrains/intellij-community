// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object FusStatesRecorder {
  private val log = logger<FusStatesRecorder>()
  private val state = ConcurrentLinkedQueue<LogEvent>()
  private var isRecordingInProgress = AtomicBoolean(false)
  private val lock = Any()

  fun recordStateAndWait(project: Project, recorderId: String): List<LogEvent>? {
    synchronized(lock) {
      state.clear()
      isRecordingInProgress.getAndSet(true)
      val subscriber = object : StatisticsEventLogListener {
        override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
          recordEvent(validatedEvent)
        }
      }
      service<EventLogListenersManager>().subscribe(subscriber, recorderId)
      try {
        project.coroutineScope.async {
          coroutineScope {
            launch {
              val stateLogger = FUStateUsagesLogger.getInstance()
              stateLogger.logApplicationStates()
              stateLogger.logProjectStates(project)
            }

            for (extension in FeatureUsageStateEventTracker.EP_NAME.extensionList) {
              launch {
                extension.reportNow()
              }
            }
          }
        }.asCompletableFuture()
          .thenCompose {
            val logger = getEventLogProvider(recorderId).logger
            if (logger is StatisticsFileEventLogger) {
              logger.flush()
            }
            else {
              CompletableFuture.completedFuture(null)
            }
          }
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