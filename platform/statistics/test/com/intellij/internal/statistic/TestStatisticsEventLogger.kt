// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.*
import java.util.*
import java.util.concurrent.CompletableFuture

class TestStatisticsEventLogger(private val session: String = "testSession",
                                private val build: String = "999.999",
                                private val bucket: String = "1",
                                private val recorderVersion: String = "1") : StatisticsEventLogger {
  val logged = ArrayList<LogEvent>()

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()

    val event = newLogEvent(session, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion, eventId, isState)
    for (datum in data) {
      event.event.addData(datum.key, datum.value)
    }
    logged.add(event)
    return CompletableFuture.completedFuture(null)
  }

  override fun getActiveLogFile(): EventLogFile? = null

  override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider

  override fun cleanup() {}

  override fun rollOver() {}
}