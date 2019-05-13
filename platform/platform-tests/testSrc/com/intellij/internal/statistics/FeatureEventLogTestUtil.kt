// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*

fun newEvent(groupId: String,
             type: String,
             time: Long = System.currentTimeMillis(),
             session: String = "session-id",
             build: String = "999.999",
             groupVersion: String = "99",
             recorderVersion: String = "1",
             bucket: String = "-1",
             count: Int = 1,
             data: Map<String, Any> = emptyMap()): LogEvent {
  val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, type, false)
  event.event.count = count
  for (datum in data) {
    event.event.addData(datum.key, datum.value)
  }
  return event
}

fun newStateEvent(groupId: String,
                  type: String,
                  time: Long = System.currentTimeMillis(),
                  session: String = "session-id",
                  build: String = "999.999",
                  groupVersion: String = "99",
                  recorderVersion: String = "1",
                  bucket: String = "-1",
                  data: Map<String, Any> = emptyMap()): LogEvent {
  val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, type, true)
  for (datum in data) {
    event.event.addData(datum.key, datum.value)
  }
  return event
}