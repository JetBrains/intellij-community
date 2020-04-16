// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.diagnostic.logging.LogFilterModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal class StatisticsEventLogConsole(val project: Project, val model: LogFilterModel)
  : LogConsoleBase(project, null, eventLogToolWindowsId, false, model) {

  override fun isActive(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(eventLogToolWindowsId)?.isVisible ?: false
  }

  fun addLogLine(line: String) {
    super.addMessage(line)
  }
}