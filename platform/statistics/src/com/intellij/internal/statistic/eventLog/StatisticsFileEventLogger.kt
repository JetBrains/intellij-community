// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException

open class StatisticsFileEventLogger(private val recorderId: String,
                                     private val sessionId: String,
                                     private val build: String,
                                     private val bucket: String,
                                     private val recorderVersion: String,
                                     private val writer: StatisticsEventLogWriter,
                                     private val systemEventIdProvider: StatisticsSystemEventIdProvider) : StatisticsEventLogger, Disposable {
  protected val logExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("StatisticsFileEventLogger: $sessionId")

  private var lastEvent: LogEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()
    group.validateEventId(eventId)
    return try {
      CompletableFuture.runAsync(Runnable {
        val context = EventContext.create(eventId, data)
        val validator = SensitiveDataValidator.getInstance(recorderId)
        val validatedEventId = validator.guaranteeCorrectEventId(group, context)
        val validatedEventData = validator.guaranteeCorrectEventData(group, context)

        val creationTime = System.currentTimeMillis()
        val event = newLogEvent(sessionId, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion,
                                validatedEventId, isState)
        for (datum in validatedEventData) {
          event.event.addData(datum.key, datum.value)
        }
        log(event, creationTime)
      }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      CompletableFuture.completedFuture(null)
    }
  }

  private fun log(event: LogEvent, createdTime: Long) {
    if (lastEvent != null && event.time - lastEventTime <= 10000 && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      lastEvent!!.event.increment()
    }
    else {
      logLastEvent()
      lastEvent = event
      lastEventTime = event.time
      lastEventCreatedTime = createdTime
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
  }

  fun flush(): CompletableFuture<Void> {
    return CompletableFuture.runAsync(Runnable {
      logLastEvent()
    }, logExecutor)
  }
}