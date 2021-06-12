// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.evaluation.EvaluationMode

class DebuggerEvaluationStatisticsCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("debugger.evaluate.usage", 3)

    @JvmField
    val DIALOG_OPEN = GROUP.registerEvent("dialog.open", EventFields.Enum("mode", EvaluationMode::class.java))

    @JvmField
    val EVALUATE = GROUP.registerEvent("evaluate", EventFields.Enum("mode", EvaluationMode::class.java))

    @JvmField
    val MODE_SWITCH = GROUP.registerEvent("mode.switch", EventFields.Enum("mode", EvaluationMode::class.java))

  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}