package com.intellij.execution.multilaunch.statistics

import com.intellij.execution.impl.statistics.RunConfigurationTypeDefs
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension

internal class MultiLaunchConfigurationUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): String = RunConfigurationTypeDefs.TRIGGER_USAGES_GROUP_ID

  override fun getEventId(): String = "started"

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(
      FusExecutableRows.FIELD,
      MultiLaunchEventFields.ACTIVATE_TOOL_WINDOWS_FIELD)
  }
}