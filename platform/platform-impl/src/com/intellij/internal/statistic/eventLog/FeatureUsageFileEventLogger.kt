/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.Disposable
import com.intellij.util.ConcurrencyUtil
import java.io.File
import java.util.*

open class FeatureUsageFileEventLogger(private val sessionId: String,
                                       private val build: String,
                                       private val bucket: String,
                                       private val recorderVersion: String,
                                       private val writer: FeatureUsageEventWriter) : FeatureUsageEventLogger, Disposable {
  protected val myLogExecutor = ConcurrencyUtil.newSingleThreadExecutor(javaClass.simpleName)

  private var lastEvent: LogEvent? = null
  private var lastEventTime: Long = 0
  private var lastEventCreatedTime: Long = 0

  override fun log(group: FeatureUsageGroup, action: String, isState: Boolean) {
    log(group, action, Collections.emptyMap(), isState)
  }

  override fun log(group: FeatureUsageGroup, action: String, data: Map<String, Any>, isState: Boolean) {
    val eventTime = System.currentTimeMillis()
    myLogExecutor.execute(Runnable {
      val creationTime = System.currentTimeMillis()
      val event = newLogEvent(sessionId, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion, action, isState)
      for (datum in data) {
        event.event.addData(datum.key, datum.value)
      }
      log(writer, event, creationTime)
    })
  }

  private fun log(writer: FeatureUsageEventWriter, event: LogEvent, createdTime: Long) {
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

  private fun logLastEvent(writer: FeatureUsageEventWriter) {
    lastEvent?.let {
      if (it.event.isEventGroup()) {
        it.event.addData("last", lastEventTime)
      }
      it.event.addData("created", lastEventCreatedTime)
      writer.log(LogEventSerializer.toString(it))
    }
    lastEvent = null
  }

  override fun getLogFiles(): List<File> {
    return writer.getFiles()
  }

  override fun dispose() {
    dispose(writer)
  }

  private fun dispose(writer: FeatureUsageEventWriter) {
    myLogExecutor.execute(Runnable {
      logLastEvent(writer)
    })
    myLogExecutor.shutdown()
  }
}