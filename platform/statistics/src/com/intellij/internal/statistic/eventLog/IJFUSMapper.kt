// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import org.jetbrains.annotations.ApiStatus

/**
 * Provides mapping of machine ids between 2 recorders
 */
@ApiStatus.Internal
class IJFUSMapper: ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("map.ml.fus", 1, "IJ_MAP")
  private val ML_RECORDER = "ML"
  private val FUS_RECORDER = "FUS"

  override fun getGroup(): EventLogGroup = GROUP

  private val mlMachineId = EventFields.StringValidatedByRegexpReference("ml_machine_id", "hash")
  private val fusMachineId = EventFields.StringValidatedByRegexpReference("fus_machine_id", "hash")

  private val report = GROUP.registerEvent("paired", mlMachineId, fusMachineId, "Paired FUS and ML machine_id")

  override fun getMetrics(): Set<MetricEvent> {
    val mlConfig = EventLogConfigOptionsService.getInstance().getOptions(ML_RECORDER)
    val fusConfig = EventLogConfigOptionsService.getInstance().getOptions(FUS_RECORDER)
    return setOf(report.metric(
      MachineIdManager.getAnonymizedMachineId("JetBrains${ML_RECORDER}${mlConfig.machineIdSalt}" ?: ""),
      MachineIdManager.getAnonymizedMachineId("JetBrains${FUS_RECORDER}${fusConfig.machineIdSalt}" ?: ""),
    ))
  }
}
