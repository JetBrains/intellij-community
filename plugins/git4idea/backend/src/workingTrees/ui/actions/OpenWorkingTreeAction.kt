// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitWorkingTree
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES

internal class OpenWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val data = e.getData(SELECTED_WORKING_TREES)
    e.presentation.isEnabled = isEnabledFor(data, e.project)
  }

  private fun isEnabledFor(trees: List<GitWorkingTree>?, project: Project?): Boolean {
    if (project == null || trees == null || trees.size != 1 || trees[0].isCurrent) {
      return false
    }
    val workingTree = trees[0]
    return !GitCreateWorkingTreeService.getInstance().isWorkingTreeCreationInProgress(workingTree) && !workingTree.isPrunable
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val data = e.getData(SELECTED_WORKING_TREES)
    if (!isEnabledFor(data, project)) return

    val tree = data!!.first()
    GitWorkingTreesService.getInstance(project).openWorkingTreeProject(tree)
  }
}