// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.*
import java.util.*

class TestStatisticsEventLogger(private val session: String = "testSession",
                                private val build: String = "999.999",
                                private val bucket: String = "1",
                                private val recorderVersion: String = "1") : StatisticsEventLogger {
  val logged = ArrayList<LogEvent>()

  override fun log(group: EventLogGroup, eventId: String, isState: Boolean) = log(group, eventId, Collections.emptyMap(), isState)

  override fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) {
    val eventTime = System.currentTimeMillis()

    val event = newLogEvent(session, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion, eventId, isState)
    for (datum in data) {
      event.event.addData(datum.key, datum.value)
    }
    logged.add(event)
  }

  override fun getActiveLogFile(): EventLogFile? = null

  override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider

  override fun cleanup() {}

  override fun rollOver() {}
}