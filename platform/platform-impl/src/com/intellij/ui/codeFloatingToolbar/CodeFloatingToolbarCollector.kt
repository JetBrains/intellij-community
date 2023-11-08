// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object CodeFloatingToolbarCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("code.floating.toolbar", 2)

  private val SHOWN: EventId2<Int, Boolean> = GROUP.registerEvent("shown", EventFields.Int("lines_selected"), EventFields.Boolean("top_to_bottom"))

  fun toolbarShown(startOffset: Int, endOffset: Int, lines: Int) {
    SHOWN.log(lines, startOffset <= endOffset)
  }
}