// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

open class StatisticsFileEventLogger(private val recorderId: String,
                                     private val sessionId: String,
                                     private val headless: Boolean,
                                     private val build: String,
                                     private val bucket: String,
                                     private val recorderVersion: String,
                                     private val writer: StatisticsEventLogWriter,
                                     private val systemEventIdProvider: StatisticsSystemEventIdProvider,
                                     private val mergeStrategy: StatisticsEventMergeStrategy = FilteredEventMergeStrategy(emptySet())
) : StatisticsEventLogger, Disposable {
  protected val logExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("StatisticsFileEventLogger: $sessionId", 1)

  private var lastEvent: FusEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0
  private var eventMergeTimeoutMs: Long
  private var lastEventFlushFuture: ScheduledFuture<CompletableFuture<Void>>? = null

  init {
    if (StatisticsRecorderUtil.isTestModeEnabled(recorderId)) {
      eventMergeTimeoutMs = 500L
    }
    else {
      eventMergeTimeoutMs = 10000L
    }
  }

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()
    group.validateEventId(eventId)
    return try {
      CompletableFuture.runAsync(Runnable {
        val validator = IntellijSensitiveDataValidator.getInstance(recorderId)
        if (!validator.isGroupAllowed(group)) return@Runnable
        val event = LogEvent(sessionId, build, bucket, eventTime,
          LogEventGroup(group.id, group.version.toString()),
          recorderVersion,
          LogEventAction(eventId, isState, HashMap(data))).escape()
        val validatedEvent = validator.validateEvent(event)
        if (validatedEvent != null) {
          log(validatedEvent, System.currentTimeMillis(), eventId, data)
        }
      }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      CompletableFuture<Void>().also { it.completeExceptionally(e) }
    }
  }

  private fun log(event: LogEvent, createdTime: Long, rawEventId: String, rawData: Map<String, Any>) {
    if (lastEvent != null && event.time - lastEventTime <= eventMergeTimeoutMs && mergeStrategy.shouldMerge(lastEvent!!.validatedEvent, event)) {
      lastEventTime = event.time
      lastEvent!!.validatedEvent.event.increment()
    }
    else {
      logLastEvent()
      lastEvent = if(StatisticsRecorderUtil.isTestModeEnabled(recorderId)) FusEvent(event, rawEventId, rawData) else FusEvent(event, null, null)
      lastEventTime = event.time
      lastEventCreatedTime = createdTime
    }

    if (StatisticsRecorderUtil.isTestModeEnabled(recorderId)) {
      lastEventFlushFuture?.cancel(false)
      // call flush() instead of logLastEvent() directly so that logLastEvent is executed on the logExecutor thread and not on scheduled executor pool thread
      lastEventFlushFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule(this::flush, eventMergeTimeoutMs, TimeUnit.MILLISECONDS)
    }
  }

  private fun logLastEvent() {
    lastEvent?.let {
      val event = it.validatedEvent.event
      if (event.isEventGroup()) {
        event.data["last"] = lastEventTime
      }
      event.data["created"] = lastEventCreatedTime
      var systemEventId = systemEventIdProvider.getSystemEventId(recorderId)
      event.data["system_event_id"] = systemEventId
      systemEventIdProvider.setSystemEventId(recorderId, ++systemEventId)

      if (headless) {
        event.data["system_headless"] = true
      }
      writer.log(it.validatedEvent)
      ApplicationManager.getApplication().getService(EventLogListenersManager::class.java)
        .notifySubscribers(recorderId, it.validatedEvent, it.rawEventId, it.rawData)
    }
    lastEvent = null
  }

  override fun getActiveLogFile(): EventLogFile? {
    return writer.getActiveFile()
  }

  override fun getLogFilesProvider(): EventLogFilesProvider {
    return writer.getLogFilesProvider()
  }

  override fun cleanup() {
    writer.cleanup()
  }

  override fun rollOver() {
    writer.rollOver()
  }

  override fun dispose() {
    flush()
    logExecutor.shutdown()
    Disposer.dispose(writer)
  }

  fun flush(): CompletableFuture<Void> {
    return CompletableFuture.runAsync({ logLastEvent() }, logExecutor)
  }

  private data class FusEvent(val validatedEvent: LogEvent,
                              val rawEventId: String?,
                              val rawData: Map<String, Any>?)
}