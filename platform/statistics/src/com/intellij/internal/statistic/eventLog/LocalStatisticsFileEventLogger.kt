// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Event logger that only notifies subscribers listed in [com.intellij.internal.statistic.eventLog.EventLogListenersManager.isLocalAllowed]
 */
class LocalStatisticsFileEventLogger internal constructor(
  private val recorderId: String,
  private val build: String,
  private val recorderVersion: String,
  private val mergeStrategy: StatisticsEventMergeStrategy = FilteredEventMergeStrategy(emptySet()),
  private val coroutineScope: CoroutineScope,
) : StatisticsEventLogger, Disposable {
  private val semaphore = Semaphore(1)

  private val eventMergeTimeoutMs: Long = 3000L

  private var lastEvent: FusEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0

  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    dataProvider: () -> Map<String, Any>?,
    isState: Boolean,
  ): CompletableFuture<*> {
    val eventTime = System.currentTimeMillis()
    group.validateEventId(eventId)
    try {
      return coroutineScope.launch {
        semaphore.withPermit {
          IntellijSensitiveDataValidator.getInstance(recorderId)
          val validator = IntellijSensitiveDataValidator.getInstance(recorderId)
          if (!validator.isGroupAllowed(group)) {
            return@withPermit
          }

          val data = dataProvider() ?: return@withPermit
          val logEventGroup = LogEventGroup(group.id, group.version.toString())
          val logEventAction = LogEventAction(eventId, isState, HashMap(data))
          val event = LogEvent(
            session = "local",
            build = build,
            bucket = "",
            time = eventTime,
            group = logEventGroup,
            recorderVersion = recorderVersion,
            event = logEventAction,
          ).escapeExceptData()
          val validatedEvent = validator.validateEvent(event)
          if (validatedEvent != null) {
            log(validatedEvent, System.currentTimeMillis())
          }
        }
      }.asCompletableFuture()
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      return CompletableFuture<Void>().also { it.completeExceptionally(e) }
    }
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
  }

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<*> {
    return logAsync(group = group, eventId = eventId, dataProvider = { data }, isState = isState)
  }

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
      ApplicationManager.getApplication()
        ?.getServiceIfCreated(EventLogListenersManager::class.java)
        ?.notifySubscribers(recorderId, it.validatedEvent, it.rawEventId, it.rawData, true)
    }
    lastEvent = null
  }

  override fun getActiveLogFile(): Nothing? = null

  override fun getLogFilesProvider(): EmptyEventLogFilesProvider = EmptyEventLogFilesProvider

  override fun cleanup() {
  }

  override fun rollOver() {
  }

  override fun dispose() {
    flush()
  }

  fun flush(): CompletableFuture<*> {
    return coroutineScope.launch { semaphore.withPermit { logLastEvent() } }.asCompletableFuture()
  }
}

private data class FusEvent(val validatedEvent: LogEvent, val rawEventId: String?, val rawData: Map<String, Any>?)
