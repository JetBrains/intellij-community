// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.io.File
import java.util.*

open class StatisticsFileEventLogger(private val recorderId: String,
                                     private val sessionId: String,
                                     private val build: String,
                                     private val bucket: String,
                                     private val recorderVersion: String,
                                     private val writer: StatisticsEventLogWriter) : StatisticsEventLogger, Disposable {
  protected val logExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("StatisticsFileEventLogger: $sessionId")

  private var lastEvent: LogEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0

  override fun log(group: EventLogGroup, eventId: String, isState: Boolean) {
    log(group, eventId, Collections.emptyMap(), isState)
  }

  override fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) {
    val eventTime = System.currentTimeMillis()
    logExecutor.execute(Runnable {
      val context = EventContext.create(eventId, data)
      val validator = SensitiveDataValidator.getInstance(recorderId)
      val validatedEventId = validator.guaranteeCorrectEventId(group, context)
      val validatedEventData = validator.guaranteeCorrectEventData(group, context)

      val creationTime = System.currentTimeMillis()
      val event = newLogEvent(sessionId, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion, validatedEventId, isState)
      for (datum in validatedEventData) {
        event.event.addData(datum.key, datum.value)
      }
      log(writer, event, creationTime)
    })
  }

  private fun log(writer: StatisticsEventLogWriter, event: LogEvent, createdTime: Long) {
    if (lastEvent != null && event.time - lastEventTime <= 10000 && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      lastEvent!!.event.increment()
    }
    else {
      logLastEvent(writer)
      lastEvent = event
      lastEventTime = event.time
      lastEventCreatedTime = createdTime
    }
  }

  private fun logLastEvent(writer: StatisticsEventLogWriter) {
    lastEvent?.let {
      if (it.event.isEventGroup()) {
        it.event.addData("last", lastEventTime)
      }
      it.event.addData("created", lastEventCreatedTime)
      writer.log(it)
    }
    lastEvent = null
  }

  override fun getActiveLogFile(): EventLogFile? {
    return writer.getActiveFile()
  }

  override fun getLogFiles(): List<EventLogFile> {
    return writer.getFiles()
  }

  override fun cleanup() {
    writer.cleanup()
  }

  override fun rollOver() {
    writer.rollOver()
  }

  override fun dispose() {
    dispose(writer)
  }

  private fun dispose(writer: StatisticsEventLogWriter) {
    logExecutor.execute(Runnable {
      logLastEvent(writer)
    })
    logExecutor.shutdown()
  }
}