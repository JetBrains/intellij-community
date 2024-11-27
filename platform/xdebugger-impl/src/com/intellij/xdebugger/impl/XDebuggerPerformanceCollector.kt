// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project

internal object XDebuggerPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("debugger.performance", 5)

  private val EXECUTION_POINT_REACHED = GROUP.registerEvent(
    "execution.point.reached",
    EventFields.FileType,
    ActionsEventLogGroup.ACTION_ID,
    EventFields.DurationMs
  )

  private val BREAKPOINT_REACHED = GROUP.registerEvent("execution.point.breakpoint.reached", EventFields.FileType)

  @JvmStatic
  fun logExecutionPointReached(project: Project?, fileType: FileType?, actionId: String, durationMs: Long) {
    EXECUTION_POINT_REACHED.log(project, fileType, actionId, durationMs)
  }

  @JvmStatic
  fun logBreakpointReached(project: Project?, fileType: FileType?) {
    BREAKPOINT_REACHED.log(project, fileType)
  }
}
