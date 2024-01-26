// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture

/**
 * Event logger that only notifies subscribers listed in [com.intellij.internal.statistic.eventLog.EventLogListenersManager.isLocalAllowed]
 */
class LocalStatisticsFileEventLogger(
  private val recorderId: String,
  private val build: String,
  private val recorderVersion: String,
  private val mergeStrategy: StatisticsEventMergeStrategy = FilteredEventMergeStrategy(emptySet())
) : StatisticsEventLogger, Disposable {
  private val logExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("LocalStatisticsFileEventLogger", 1)

  private val eventMergeTimeoutMs: Long = 3000L

  private var lastEvent: FusEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0
  private var lastEventFlushFuture: ScheduledFuture<CompletableFuture<Void>>? = null

  override fun logAsync(group: EventLogGroup, eventId: String, dataProvider: () -> Map<String, Any>?, isState: Boolean): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()
    group.validateEventId(eventId)
    return try {
      CompletableFuture.runAsync(Runnable {
        val validator = IntellijSensitiveDataValidator.getInstance(recorderId)
        if (!validator.isGroupAllowed(group)) return@Runnable
        val data = dataProvider() ?: return@Runnable
        val event = LogEvent("local", build, "", eventTime,
                             LogEventGroup(group.id, group.version.toString()),
                             recorderVersion,
                             LogEventAction(eventId, isState, HashMap(data)))
          .escapeExceptData()
        val validatedEvent = validator.validateEvent(event)
        if (validatedEvent != null) {
          log(validatedEvent, System.currentTimeMillis())
        }
      }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      CompletableFuture<Void>().also { it.completeExceptionally(e) }
    }
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) = Unit

  override fun logAsync(group: EventLogGroup, eventId: String,
                        data: Map<String, Any>, isState: Boolean) = logAsync(group, eventId, { data }, isState)

  private fun log(event: LogEvent, createdTime: Long) {
    if (lastEvent != null && event.time - lastEventTime <= eventMergeTimeoutMs && mergeStrategy.shouldMerge(lastEvent!!.validatedEvent, event)) {
      lastEventTime = event.time
      lastEvent!!.validatedEvent.event.increment()
    }
    else {
      logLastEvent()
      lastEvent = FusEvent(event, null, null)
      lastEventTime = event.time
      lastEventCreatedTime = createdTime
    }
  }

  private fun logLastEvent() {
    lastEvent?.let {
      val event = it.validatedEvent.event
      if (event.isEventGroup()) {
        event.data["last"] = lastEventTime
      }
      event.data["created"] = lastEventCreatedTime

      ApplicationManager.getApplication().getService(EventLogListenersManager::class.java)
        .notifySubscribers(recorderId, it.validatedEvent, it.rawEventId, it.rawData, true)
    }
    lastEvent = null
  }

  override fun getActiveLogFile() = null

  override fun getLogFilesProvider() = EmptyEventLogFilesProvider

  override fun cleanup() = Unit

  override fun rollOver() = Unit

  override fun dispose() {
    lastEventFlushFuture?.cancel(false)
    flush()
    logExecutor.shutdown()
  }

  fun flush(): CompletableFuture<Void> {
    return CompletableFuture.runAsync({ logLastEvent() }, logExecutor)
  }

  private data class FusEvent(val validatedEvent: LogEvent,
                              val rawEventId: String?,
                              val rawData: Map<String, Any>?)
}