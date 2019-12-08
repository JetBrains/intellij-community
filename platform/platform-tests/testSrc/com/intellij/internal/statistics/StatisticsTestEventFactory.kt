// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*

object StatisticsTestEventFactory {
  const val DEFAULT_SESSION_ID = "a96c3f145"

  fun newEvent(groupId: String = "group.id",
               eventId: String = "event.id",
               time: Long = System.currentTimeMillis(),
               session: String = DEFAULT_SESSION_ID,
               build: String = "999.999",
               groupVersion: String = "99",
               recorderVersion: String = "1",
               bucket: String = "0",
               count: Int = 1,
               data: Map<String, Any> = emptyMap()): LogEvent {
    val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, eventId, false)
    event.event.count = count
    for (datum in data) {
      event.event.addData(datum.key, datum.value)
    }
    return event
  }

  fun newStateEvent(groupId: String = "group.id",
                    eventId: String = "event.id",
                    time: Long = System.currentTimeMillis(),
                    session: String = DEFAULT_SESSION_ID,
                    build: String = "999.999",
                    groupVersion: String = "99",
                    recorderVersion: String = "1",
                    bucket: String = "0",
                    data: Map<String, Any> = emptyMap()): LogEvent {
    val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, eventId, true)
    for (datum in data) {
      event.event.addData(datum.key, datum.value)
    }
    return event
  }
}