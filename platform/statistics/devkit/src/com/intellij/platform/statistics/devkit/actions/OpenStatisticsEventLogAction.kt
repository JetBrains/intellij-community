// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.statistics.devkit.actions

import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.statistics.devkit.toolwindow.eventLogToolWindowId
import com.intellij.platform.statistics.devkit.toolwindow.hostEventLogToolWindowId

/**
 * Opens a toolwindow with feature usage statistics event log
 */
internal class OpenStatisticsEventLogAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val toolWindowId = if (IdeProductMode.isFrontend) eventLogToolWindowId else hostEventLogToolWindowId
    ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.activate(null)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = StatisticsRecorderUtil.isAnyTestModeEnabled()
  }
}