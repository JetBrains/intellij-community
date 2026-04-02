// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitCreateWorkingTreeService
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService

internal class CheckoutWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val workingTree = e.getData(SELECTED_WORKING_TREES)?.singleOrNull()
    val repository = e.getData(CURRENT_REPOSITORY)
    e.presentation.isEnabledAndVisible = isEnabledFor(workingTree, repository)
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val workingTree = e.getData(SELECTED_WORKING_TREES)?.singleOrNull() ?: return
    val repository = e.getData(CURRENT_REPOSITORY) ?: return
    if (!isEnabledFor(workingTree, repository)) return

    GitWorkingTreesService.getInstance(repository.project).checkoutWorktreeInCurrentRepository(repository, workingTree)
  }

  private fun isEnabledFor(workingTree: GitWorkingTree?, repository: GitRepository?): Boolean {
    if (workingTree == null || repository == null || workingTree.isCurrent) return false
    if (GitCreateWorkingTreeService.getInstance().isWorkingTreeCreationInProgress(workingTree)) return false

    val branch = workingTree.currentBranch ?: return false
    return repository.currentBranch != branch
  }
}
