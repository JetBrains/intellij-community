// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL
import com.intellij.vcs.commit.CommitWorkflowManager.Companion.setCommitFromLocalChanges

private class ChangesViewToolWindowFactory : VcsToolWindowFactory() {
  override fun updateState(project: Project, toolWindow: ToolWindow) {
    super.updateState(project, toolWindow)
    toolWindow.stripeTitle = project.vcsManager.allActiveVcss.singleOrNull()?.displayName ?: ToolWindowId.VCS
  }
}

private class CommitToolWindowFactory : VcsToolWindowFactory() {
  override fun init(window: ToolWindow) {
    super.init(window)

    window as ToolWindowEx
    window.setAdditionalGearActions(DefaultActionGroup(SwitchToCommitDialogAction()))
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return super.shouldBeAvailable(project) && project.isCommitToolWindow
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(HIDE_ID_LABEL, "true")
    super.createToolWindowContent(project, toolWindow)
  }
}

private class SwitchToCommitDialogAction : DumbAwareAction() {
  init {
    templatePresentation.text = message("action.switch.to.commit.dialog.text")
  }

  override fun actionPerformed(e: AnActionEvent) {
    setCommitFromLocalChanges(false)

    val commitAction = ActionManager.getInstance().getAction(ACTION_CHECKIN_PROJECT) ?: return
    invokeAction(commitAction, e.dataContext, e.place, e.inputEvent, null)
  }
}