// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.COMMIT_TOOLWINDOW_ID
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL

private class ChangesViewToolWindowFactory : VcsToolWindowFactory() {
  override fun updateState(project: Project, toolWindow: ToolWindow) {
    super.updateState(project, toolWindow)
    toolWindow.stripeTitle = project.vcsManager.allActiveVcss.singleOrNull()?.displayName ?: ChangesViewContentManager.TOOLWINDOW_ID
  }
}

private class CommitToolWindowFactory : VcsToolWindowFactory() {
  override fun init(window: ToolWindow) {
    super.init(window)

    window as ToolWindowEx
    window.setAdditionalGearActions(ActionManager.getInstance().getAction("CommitView.GearActions") as ActionGroup)
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return super.shouldBeAvailable(project) && project.isCommitToolWindowShown
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(HIDE_ID_LABEL, "true")
    super.createToolWindowContent(project, toolWindow)
  }
}

internal class SwitchToCommitDialogHint(toolWindow: ToolWindowEx, toolbar: ActionToolbar) : ChangesViewContentManagerListener {

  private val actionToolbarTooltip =
    ActionToolbarGotItTooltip("changes.view.toolwindow", message("switch.to.commit.dialog.hint.text"),
                              toolWindow.disposable, toolbar, gearButtonOrToolbar)
  init {
    toolWindow.project.messageBus.connect(actionToolbarTooltip.tooltipDisposable).subscribe(ChangesViewContentManagerListener.TOPIC, this)
  }

  override fun toolWindowMappingChanged() = actionToolbarTooltip.hideHint(true)

  companion object {
    fun install(project: Project) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(COMMIT_TOOLWINDOW_ID) as? ToolWindowEx ?: return
      val toolbar = toolWindow.decorator.headerToolbar ?: return

      SwitchToCommitDialogHint(toolWindow, toolbar)
    }
  }
}
