// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class XDebuggerPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    val GROUP = EventLogGroup("debugger.performance", 1)

    @JvmField
    val ACTION_ID = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)

    @JvmField
    val EXECUTION_POINT_REACHED = GROUP.registerVarargEvent(
      "execution.point.reached",
      EventFields.FileType,
      ACTION_ID,
      EventFields.DurationMs
    )
  }
}
