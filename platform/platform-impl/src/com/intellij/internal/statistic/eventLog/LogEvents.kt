/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package com.intellij.internal.statistic.eventLog

import com.intellij.util.containers.ContainerUtil
import java.util.*

open class LogEvent(session: String, bucket: String, recorderId: String, recorderVersion: String, type: String) {
  val session: String = escape(session)
  val bucket: String = escape(bucket)
  val time: Long = System.currentTimeMillis()
  val recorder: LogEventRecorder = LogEventRecorder(escape(recorderId), escape(recorderVersion))
  val action: LogEventAction = LogEventAction(escape(type))

  fun shouldMerge(next: LogEvent): Boolean {
    if (session != next.session) return false
    if (bucket != next.bucket) return false
    if (recorder.id != next.recorder.id) return false
    if (recorder.version != next.recorder.version) return false
    if (action.id != next.action.id) return false
    if (action.data != next.action.data) return false
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEvent

    if (session != other.session) return false
    if (bucket != other.bucket) return false
    if (time != other.time) return false
    if (recorder != other.recorder) return false
    if (action != other.action) return false

    return true
  }

  override fun hashCode(): Int {
    var result = session.hashCode()
    result = 31 * result + bucket.hashCode()
    result = 31 * result + time.hashCode()
    result = 31 * result + recorder.hashCode()
    result = 31 * result + action.hashCode()
    return result
  }
}

class LogEventRecorder(val id: String, val version: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventRecorder

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

class LogEventAction(val id: String) {
  var data: MutableMap<String, Any> = Collections.emptyMap()

  fun addData(key: String, value: Any) {
    if (data.isEmpty()) {
      data = ContainerUtil.newHashMap()
    }

    val escapedValue = if (value is String) escape(value) else value
    data.put(escape(key), escapedValue)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventAction

    if (id != other.id) return false
    if (data != other.data) return false
    return true
  }

  override fun hashCode(): Int {
    return 31 * id.hashCode() + data.hashCode()
  }
}

private fun escape(str: String): String {
  return str.replace(" ", "_").replace("\t", "_").replace("\"", "")
}