// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.evaluation.EvaluationMode

object DebuggerEvaluationStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("debugger.evaluate.usage", 4)

  @JvmField
  val DIALOG_OPEN = GROUP.registerEvent("dialog.open", EventFields.Enum("mode", EvaluationMode::class.java))
  @JvmField
  val INPUT_FOCUS = GROUP.registerEvent("inline.input.focus")
  @JvmField
  val HISTORY_SHOW = GROUP.registerEvent("history.show")
  @JvmField
  val HISTORY_CHOOSE = GROUP.registerEvent("history.choose")
  @JvmField
  val EVALUATE = GROUP.registerEvent("evaluate", EventFields.Enum("mode", EvaluationMode::class.java))
  @JvmField
  val INLINE_EVALUATE = GROUP.registerEvent("inline.evaluate")
  @JvmField
  val WATCH_FROM_INLINE_ADD = GROUP.registerEvent("watch.from.inline.add", EventFields.InputEventByAnAction)
  @JvmField
  val MODE_SWITCH = GROUP.registerEvent("mode.switch", EventFields.Enum("mode", EvaluationMode::class.java))

  override fun getGroup(): EventLogGroup = GROUP
}