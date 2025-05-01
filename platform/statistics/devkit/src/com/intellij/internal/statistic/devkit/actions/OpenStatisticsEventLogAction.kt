// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.internal.statistic.devkit.toolwindow.eventLogToolWindowsId
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction

/**
 * Opens a toolwindow with feature usage statistics event log
 */
internal class OpenStatisticsEventLogAction : DumbAwareAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val action = ActionManager.getInstance().getAction(ActivateToolWindowAction.Manager.getActionIdForToolWindow(eventLogToolWindowsId))
    if (action != null) {
      ActionUtil.performAction(action, event)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = StatisticsRecorderUtil.isAnyTestModeEnabled()
  }
}