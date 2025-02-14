// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.trialState

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object TrialStateWidgetUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("trial.state.widget", 1)

  @JvmField
  val WIDGET_CLICKED: EventId = GROUP.registerEvent("click", "How many times the trial widget was clicked")

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}
