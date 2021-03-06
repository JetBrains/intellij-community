// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.internal.statistic.toolwindow.eventLogToolWindowsId
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.project.DumbAwareAction

/**
 * Opens a toolwindow with feature usage statistics event log
 */
internal class OpenStatisticsEventLogAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val action = ActionManager.getInstance().getAction(ActivateToolWindowAction.getActionIdForToolWindow(eventLogToolWindowsId))
    if (action != null) {
      performActionDumbAwareWithCallbacks(action, event)
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = StatisticsRecorderUtil.isAnyTestModeEnabled()
  }
}