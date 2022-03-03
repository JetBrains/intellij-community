// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class VfsUsageCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("vfs", 3)
    private val REFRESHED = GROUP.registerEvent("refreshed",
                                                EventFields.Long("start_time_ms"),
                                                EventFields.Long("finish_time_ms"),
                                                EventFields.DurationMs)

    @JvmStatic
    fun logVfsRefreshed(startedTime: Long, finishedTime: Long, duration: Long) {
      REFRESHED.log(startedTime, finishedTime, duration)
    }
  }
}