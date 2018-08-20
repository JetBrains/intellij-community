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

  override fun log(recorderId: String, action: String, isState: Boolean) {
    log(recorderId, action, Collections.emptyMap(), isState)
  }

  override fun log(recorderId: String, action: String, data: Map<String, Any>, isState: Boolean) {
    myLogExecutor.execute(Runnable {
      val event = newLogEvent(sessionId, build, bucket, recorderId, recorderVersion, action, isState)
      for (datum in data) {
        event.event.addData(datum.key, datum.value)
      }
      log(writer, event)
    })
  }

  private fun log(writer: FeatureUsageEventWriter, event: LogEvent) {
    if (lastEvent != null && event.time - lastEventTime <= 10000 && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      lastEvent!!.event.increment()
    }
    else {
      logLastEvent(writer)
      lastEvent = event
      lastEventTime = event.time
    }
  }

  private fun logLastEvent(writer: FeatureUsageEventWriter) {
    if (lastEvent != null) {
      writer.log(LogEventSerializer.toString(lastEvent!!))
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