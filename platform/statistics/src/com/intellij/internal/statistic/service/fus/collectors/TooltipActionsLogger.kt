// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

object TooltipActionsLogger {
  private const val GROUP = "tooltip.action.events"

  fun logExecute(project: Project?, inputEvent: InputEvent?) {
    val data = FeatureUsageData().addInputEvent(inputEvent, null)
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP, "execute", data)
  }

  fun logShowAll(project: Project?) {
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP, "show.all")
  }

  fun logShowDescription(project: Project?, source: String, inputEvent: InputEvent?, place: String?) {
    val data = FeatureUsageData().addData("source", source).addInputEvent(inputEvent, place)
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP, "show.description", data)
  }
}