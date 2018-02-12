/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package com.intellij.internal.statistic.eventLog

import com.intellij.util.containers.ContainerUtil

open class LogEvent(val session: String, val bucket: String,
                    recorderId: String,
                    recorderVersion: String,
                    type: String) {
  val time = System.currentTimeMillis()
  val recorder: LogEventRecorder = LogEventRecorder(removeTabsOrSpaces(recorderId), recorderVersion)
  val action: LogEventAction = LogEventAction(removeTabsOrSpaces(type))

  fun shouldMerge(next: LogEvent): Boolean {
    if (session != next.session) return false
    if (bucket != next.bucket) return false
    if (recorder.id != next.recorder.id) return false
    if (recorder.version != next.recorder.version) return false
    if (action.id != next.action.id) return false
    if (action.data != next.action.data) return false
    return true
  }

  private fun removeTabsOrSpaces(str : String) : String {
    return str.replace(" ", "_").replace("\t", "_")
  }
}

class LogEventRecorder(val id: String, val version: String)

class LogEventAction(val id: String) {
  val data: MutableMap<String, Any> = ContainerUtil.newHashMap()
}