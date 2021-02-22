// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
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
                                     private val systemEventIdProvider: StatisticsSystemEventIdProvider) : StatisticsEventLogger, Disposable {
  protected val logExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("StatisticsFileEventLogger: $sessionId", 1)

  private var lastEvent: LogEvent? = null
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
        val event = validator.validate(group.id, group.version.toString(), build, sessionId, bucket, eventTime, recorderVersion, eventId,
                                       data, isState)
        if (event != null) {
          log(event, System.currentTimeMillis())
        }
      }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      CompletableFuture.completedFuture(null)
    }
  }

  private fun log(event: LogEvent, createdTime: Long) {
    if (lastEvent != null && event.time - lastEventTime <= eventMergeTimeoutMs && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      lastEvent!!.event.increment()
    }
    else {
      logLastEvent()
      lastEvent = event
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
      if (it.event.isEventGroup()) {
        it.event.addData("last", lastEventTime)
      }
      it.event.addData("created", lastEventCreatedTime)
      var systemEventId = systemEventIdProvider.getSystemEventId(recorderId)
      it.event.addData("system_event_id", systemEventId)
      systemEventIdProvider.setSystemEventId(recorderId, ++systemEventId)

      if (headless) {
        it.event.addData("system_headless", true)
      }
      writer.log(it)
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
    return CompletableFuture.runAsync(Runnable {
      logLastEvent()
    }, logExecutor)
  }
}