// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object CodeFloatingToolbarCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("code.floating.toolbar", 1)

  private val SHOWN: EventId = GROUP.registerEvent("shown")
  private val CODE_SELECTION: EventId2<Boolean, Int> = GROUP.registerEvent("code_selection", EventFields.Boolean("top_to_bottom"), EventFields.Int("lines_selected"))

  fun toolbarShown() {
    SHOWN.log()
  }

  fun codeSelected(startOffset: Int, endOffset: Int, lines: Int) {
    CODE_SELECTION.log(startOffset <= endOffset, lines)
  }
}