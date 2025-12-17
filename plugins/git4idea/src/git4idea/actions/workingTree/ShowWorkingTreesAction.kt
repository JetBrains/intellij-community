// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.VcsShowToolWindowTabAction
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.GitWorkingTreesUtil

internal class ShowWorkingTreesAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = shouldShow(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun shouldShow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    if (ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) == null) return false
    return GitWorkingTreesService.getSingleRepositoryOrNullIfEnabled(project) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    GitWorkingTreesService.getInstance(project).workingTreesTabOpenedByUser()
    project.getMessageBus().syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
    VcsShowToolWindowTabAction.activateVcsTab(project, GitWorkingTreesUtil.TOOLWINDOW_TAB_ID, false)
  }
}