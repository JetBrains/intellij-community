package com.intellij.smartUpdate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object SmartUpdateUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("smart.update", 2)
  private val updatedEvent = GROUP.registerEvent("vcs.update", EventFields.DurationMs)
  private val buildEvent = GROUP.registerEvent("build.project", EventFields.DurationMs, EventFields.Boolean("success"))
  private val scheduledEvent = GROUP.registerEvent("scheduled")

  fun logUpdate(duration: Long) {
    updatedEvent.log(duration)
  }

  fun logBuild(duration: Long, success: Boolean) {
    buildEvent.log(duration, success)
  }

  fun logScheduled() {
    scheduledEvent.log()
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}