// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

class TooltipActionsLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  enum class Source(private val text: String) {
    Shortcut("shortcut"), Gear("gear"), MoreLink("more.link");

    override fun toString() = text
  }

  companion object {
    private val GROUP = EventLogGroup("tooltip.action.events", 1)


    private val executeEvent = GROUP.registerEvent("execute", EventFields.InputEvent)
    val showAllEvent = GROUP.registerEvent("show.all")
    private val showDescriptionEvent = GROUP.registerEvent("show.description", EventFields.Enum<Source>("source"), EventFields.InputEvent)

    @JvmStatic
    fun logExecute(project: Project?, inputEvent: InputEvent?) {
      executeEvent.log(project, FusInputEvent(inputEvent, null))
    }

    @JvmStatic
    fun logShowDescription(project: Project?, source: Source, inputEvent: InputEvent?, place: String?) {
      showDescriptionEvent.log(project, source, FusInputEvent(inputEvent, place))
    }
  }
}