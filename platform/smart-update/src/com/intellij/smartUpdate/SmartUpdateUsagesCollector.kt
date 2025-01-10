package com.intellij.smartUpdate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object SmartUpdateUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("smart.update", 3)
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

  override fun getGroup(): EventLogGroup = GROUP
}