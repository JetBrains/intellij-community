// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES

/**
 * A single action flipping between "Lock"/"Unlock" depending on the selected worktree's [GitWorkingTree.isLocked]
 * state - disabled for the current/main worktree row and for a worktree still being created.
 */
internal class GitToggleLockWorkingTreeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val data = e.getData(SELECTED_WORKING_TREES)
    val repository = e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY)
    val targetLockedState = resolveTargetLockedState(e.project, repository, data)
    e.presentation.isEnabled = targetLockedState != null
    e.presentation.text = if (targetLockedState == true) {
      GitBundle.message("action.Git.WorkingTrees.Unlock.text")
    }
    else {
      GitBundle.message("action.Git.WorkingTrees.Lock.text")
    }
  }

  // The action's own target state: locking an unlocked selection (false) or unlocking a locked one (true);
  // null when the selection is empty, mixed, or otherwise not eligible for either operation.
  private fun resolveTargetLockedState(project: Project?, repository: GitRepository?, trees: List<GitWorkingTree>?): Boolean? {
    if (project == null || repository == null || trees.isNullOrEmpty()) return null
    if (trees.any { it.isCurrent || it.isMain || GitCreateWorkingTreeService.getInstance().isWorkingTreeCreationInProgress(it) }) {
      return null
    }
    return trees.map { it.isLocked }.distinct().singleOrNull()
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val data = e.getData(SELECTED_WORKING_TREES) ?: return
    val repository = e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY) ?: return
    val targetLockedState = resolveTargetLockedState(project, repository, data) ?: return
    val tree = data.singleOrNull() ?: return

    if (targetLockedState) {
      GitWorkingTreesService.getInstance(project).unlockWorkingTree(project, repository, tree)
    }
    else {
      GitWorkingTreesService.getInstance(project).lockWorkingTree(project, repository, tree)
    }
  }
}
