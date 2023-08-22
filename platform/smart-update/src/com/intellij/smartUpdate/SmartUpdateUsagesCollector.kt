package com.intellij.smartUpdate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class SmartUpdateUsagesCollector: CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("smart.update", 1)
    private val updatedEvent = GROUP.registerEvent("vcs.update", EventFields.DurationMs)
    private val buildEvent = GROUP.registerEvent("build.project", EventFields.DurationMs, EventFields.Boolean("success"))

    fun logUpdate(duration: Long) {
      updatedEvent.log(duration)
    }

    fun logBuild(duration: Long, success: Boolean) {
      buildEvent.log(duration, success)
    }
  }
  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}