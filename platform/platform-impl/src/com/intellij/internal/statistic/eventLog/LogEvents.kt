// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.StatisticsEventEscaper.escapeFieldName
import java.util.*

fun newLogEvent(session: String,
                build: String,
                bucket: String,
                time: Long,
                groupId: String,
                groupVersion: String,
                recorderVersion: String,
                event: LogEventAction): LogEvent {
  return LogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, event)
}

fun newLogEvent(session: String,
                build: String,
                bucket: String,
                time: Long,
                groupId: String,
                groupVersion: String,
                recorderVersion: String,
                eventId: String,
                isState: Boolean = false): LogEvent {
  val event = LogEventAction(StatisticsEventEscaper.escape(eventId), isState, 1)
  return LogEvent(session, build, bucket, time, groupId, groupVersion, recorderVersion, event)
}

open class LogEvent(session: String,
                    build: String,
                    bucket: String,
                    eventTime: Long,
                    groupId: String,
                    groupVersion: String,
                    recorderVersion: String,
                    eventAction: LogEventAction) {
  val recorderVersion: String = StatisticsEventEscaper.escape(recorderVersion)
  val session: String = StatisticsEventEscaper.escape(session)
  val build: String = StatisticsEventEscaper.escape(build)
  val bucket: String = StatisticsEventEscaper.escape(bucket)
  val time: Long = eventTime
  val group: LogEventGroup = LogEventGroup(StatisticsEventEscaper.escape(groupId), StatisticsEventEscaper.escape(groupVersion))
  val event: LogEventAction = eventAction

  fun shouldMerge(next: LogEvent): Boolean {
    if (session != next.session) return false
    if (bucket != next.bucket) return false
    if (build != next.build) return false
    if (recorderVersion != next.recorderVersion) return false
    if (group.id != next.group.id) return false
    if (group.version != next.group.version) return false
    if (!event.shouldMerge(next.event)) return false
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEvent

    if (session != other.session) return false
    if (bucket != other.bucket) return false
    if (build != other.build) return false
    if (time != other.time) return false
    if (group != other.group) return false
    if (recorderVersion != other.recorderVersion) return false
    if (event != other.event) return false

    return true
  }

  override fun hashCode(): Int {
    var result = session.hashCode()
    result = 31 * result + bucket.hashCode()
    result = 31 * result + build.hashCode()
    result = 31 * result + time.hashCode()
    result = 31 * result + group.hashCode()
    result = 31 * result + recorderVersion.hashCode()
    result = 31 * result + event.hashCode()
    return result
  }
}

class LogEventGroup(val id: String, val version: String) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventGroup

    if (id != other.id) return false
    if (version != other.version) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + version.hashCode()
    return result
  }
}

class LogEventAction(val id: String, var state: Boolean = false, var count: Int = 1) {
  var data: MutableMap<String, Any> = Collections.emptyMap()

  fun increment() {
    count++
  }

  fun isEventGroup(): Boolean {
    return count > 1
  }

  fun shouldMerge(next: LogEventAction): Boolean {
    if (state || next.state) return false

    if (id != next.id) return false
    if (data != next.data) return false
    return true
  }

  fun addData(key: String, value: Any) {
    if (data.isEmpty()) {
      data = HashMap()
    }

    val escapedValue = when (value) {
      is String -> StatisticsEventEscaper.escape(value)
      is List<*> -> value.map { if (it is String) StatisticsEventEscaper.escape(it) else it }
      else -> value
    }
    data[escapeFieldName(key)] = escapedValue
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventAction

    if (id != other.id) return false
    if (count != other.count) return false
    if (data != other.data) return false
    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + count
    result = 31 * result + data.hashCode()
    return result
  }
}

@Deprecated("Use StatisticsEventEscaper#escape", ReplaceWith("StatisticsEventEscaper.escape(str)"))
fun escape(str: String): String {
  return StatisticsEventEscaper.escape(str)
}