// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.ide.actions.ToolWindowMoveAction.Anchor
import com.intellij.ide.actions.ToolWindowViewModeAction.ViewMode
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.toolWindow.ToolWindowEventSource

class ToolWindowEventLogGroup : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("toolwindow", 61)

    @JvmField
    val TOOLWINDOW_ID = EventFields.StringValidatedByCustomRule("id", "toolwindow")

    @JvmField
    val VIEW_MODE: EnumEventField<ViewMode> = Enum("ViewMode", ViewMode::class.java) { mode: ViewMode -> mode.name }
    @JvmField
    val LOCATION: EnumEventField<Anchor> = Enum("Location", Anchor::class.java) { location: Anchor -> location.name }
    @JvmField
    val SOURCE: EnumEventField<ToolWindowEventSource> = Enum("Source", ToolWindowEventSource::class.java)

    @JvmField
    val ACTIVATED = registerToolwindowEvent("activated")
    @JvmField
    val SHOWN = registerToolwindowEvent("shown")
    @JvmField
    val HIDDEN = registerToolwindowEvent("hidden")

    private fun registerToolwindowEvent(eventId: String): VarargEventId {
      return GROUP.registerVarargEvent(eventId, TOOLWINDOW_ID, EventFields.PluginInfo, VIEW_MODE, LOCATION, SOURCE)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}