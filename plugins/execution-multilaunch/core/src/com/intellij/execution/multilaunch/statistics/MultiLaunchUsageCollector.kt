package com.intellij.execution.multilaunch.statistics

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MultiLaunchUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("run.configuration.multilaunch", 1)
  private val CREATED_ORIGIN = GROUP.registerEvent("configuration.activated",
                                                   CreatedOrigin.CREATED_FIELD,
                                                   CreatedOrigin.ORIGIN_FIELD)
  override fun getGroup() = GROUP

  fun logCreated(configuration: MultiLaunchConfiguration) {
    CREATED_ORIGIN.log(true, configuration.origin)
  }

  fun logActivated(configuration: MultiLaunchConfiguration) {
    CREATED_ORIGIN.log(false, configuration.origin)
  }
}