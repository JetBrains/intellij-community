// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager

/**
 * @author Konstantin Bulenkov
 */
class UpdateManagerUsagesCollector : ApplicationUsagesCollector(), AllowedDuringStartupCollector {
  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("platform.installer", 2)
    private val UPDATE_MANAGER: EventId1<String?> =
      GROUP.registerEvent(
        "Update Manager",
        EventFields.String("value", arrayListOf("Toolbox App", "Snap", "Other", "IDE"))
      )
  }

  override fun getMetrics(): Set<MetricEvent> = setOf(
    UPDATE_MANAGER.metric(when (ExternalUpdateManager.ACTUAL) {
      ExternalUpdateManager.TOOLBOX -> "Toolbox App"
      ExternalUpdateManager.SNAP -> "Snap"
      ExternalUpdateManager.UNKNOWN -> "Other"
      null -> "IDE"
    }))

  override fun getGroup(): EventLogGroup = GROUP
}
