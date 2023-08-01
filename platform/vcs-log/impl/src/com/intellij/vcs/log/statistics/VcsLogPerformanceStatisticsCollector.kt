// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object VcsLogPerformanceStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("vcs.log.performance", 2)
  val FILE_HISTORY_COMPUTING = GROUP.registerEvent("file.history.computed",
                                                   EventFields.String("vcs", listOf("Git", "hg4idea", "Perforce")),
                                                   EventFields.Boolean("with_index"),
                                                   EventFields.DurationMs)
  val FILE_HISTORY_COLLECTING_RENAMES = GROUP.registerEvent("file.history.collected.renames", EventFields.DurationMs)

  override fun getGroup() = GROUP
}
