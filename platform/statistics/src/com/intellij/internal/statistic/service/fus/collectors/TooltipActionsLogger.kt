// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.InputEventPlace
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

object TooltipActionsLogger {
  private val GROUP = EventLogGroup.byId("tooltip.action.events")

  private val executeEvent = GROUP.registerEvent("execute", EventFields.Project, EventFields.InputEvent)
  val showAllEvent = GROUP.registerEvent("show.all", EventFields.Project)
  private val showDescriptionEvent = GROUP.registerEvent("show.description", EventFields.Project, EventFields.String("source"), EventFields.InputEvent)

  fun logExecute(project: Project?, inputEvent: InputEvent?) {
    executeEvent.log(project, InputEventPlace(inputEvent, null))
  }

  fun logShowDescription(project: Project?, source: String, inputEvent: InputEvent?, place: String?) {
    showDescriptionEvent.log(project, source, InputEventPlace(inputEvent, place))
  }
}