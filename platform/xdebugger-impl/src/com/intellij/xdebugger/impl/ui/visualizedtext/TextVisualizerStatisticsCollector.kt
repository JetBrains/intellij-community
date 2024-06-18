// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.ui.VisualizedContentTab

internal object TextVisualizerStatisticsCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("debugger.visualized.text", 1)
  private val SHOWN = GROUP.registerEvent("shown", EventFields.Enum("contentType", TextVisualizerContentType::class.java))

  fun reportContentShown(project: Project, type: TextVisualizerContentType) =
    SHOWN.log(project, type)

  override fun getGroup(): EventLogGroup = GROUP
}

internal enum class TextVisualizerContentType {
  RAW,
  JSON,
  JWT,
  HTML,
  XML,
  URLEncoded,
}

internal interface VisualizedContentTabWithStats : VisualizedContentTab {
  val contentTypeForStats: TextVisualizerContentType

  override fun onShown(project: Project, firstTime: Boolean) {
    if (!firstTime) return

    TextVisualizerStatisticsCollector.reportContentShown(project, contentTypeForStats)
  }
}

