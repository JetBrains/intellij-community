// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui.actions

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES

internal class RemoveWorkingTreeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val data = e.getData(SELECTED_WORKING_TREES)
    val repository = e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY)
    e.presentation.isEnabled = isEnabledFor(data, e.project, repository)
  }

  private fun isEnabledFor(trees: List<GitWorkingTree>?, project: Project?, repository: GitRepository?): Boolean {
    if (project == null || repository == null || trees.isNullOrEmpty()) return false
    val creationService = GitCreateWorkingTreeService.getInstance()
    val worktreesService = GitWorkingTreesService.getInstance(project)
    return trees.all {
      !it.isCurrent && !it.isMain &&
      !creationService.isWorkingTreeCreationInProgress(it) &&
      !worktreesService.isWorkingTreeDeletionInProgress(it)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val data = e.getData(SELECTED_WORKING_TREES)
    val repository = e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY) ?: return
    if (!isEnabledFor(data, project, repository)) return

    val trees = data ?: return
    val result = showDeleteWorktreeDialog(trees)
    if (result == Messages.YES) {
      GitWorkingTreesService.getInstance(project).deleteWorkingTrees(project, trees, repository)
    }
  }

  private fun showDeleteWorktreeDialog(trees: List<GitWorkingTree>): Int {
    val singleTree = trees.singleOrNull()
    val (message, title, yesText) = if (singleTree != null) {
      Triple(
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.message", singleTree.path.presentableUrl),
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.title"),
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.yes.option")
      )
    }
    else {
      Triple(
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.message", trees.size),
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.title"),
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.yes.option")
      )
    }

    return Messages.showYesNoDialog(
      message,
      title,
      yesText,
      CommonBundle.getCancelButtonText(),
      AllIcons.General.QuestionDialog)
  }
}