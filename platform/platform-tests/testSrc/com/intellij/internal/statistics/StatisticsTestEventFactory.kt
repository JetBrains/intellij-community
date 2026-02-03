// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.escape
import com.intellij.internal.statistic.eventLog.newLogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEvent

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
    val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, eventId, false, data, count)
    return event.escape()
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
    val event = newLogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, eventId, true, data)
    return event.escape()
  }
}