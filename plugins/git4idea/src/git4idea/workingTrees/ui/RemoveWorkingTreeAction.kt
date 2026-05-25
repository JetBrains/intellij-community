// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitCreateWorkingTreeService
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService

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
    return project != null && repository != null && !trees.isNullOrEmpty() && trees.all {
      !it.isCurrent && !it.isMain && !GitCreateWorkingTreeService.getInstance().isWorkingTreeCreationInProgress(it)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val data = e.getData(SELECTED_WORKING_TREES)
    val repository = e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY)
    if (!isEnabledFor(data, project, repository)) return

    val result = if (data!!.size == 1) {
      val tree = data.first()
      Messages.showYesNoDialog(GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.message", tree.path.presentableUrl),
                               GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.title"),
                               GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.yes.option"),
                               CommonBundle.getCancelButtonText(),
                               AllIcons.General.QuestionDialog)
    }
    else {
      Messages.showYesNoDialog(GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.message", data.size),
                               GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.title"),
                               GitBundle.message("Git.WorkingTrees.dialog.delete.worktrees.yes.option"),
                               CommonBundle.getCancelButtonText(),
                               AllIcons.General.QuestionDialog)
    }

    if (result == Messages.YES) {
      for (tree in data) {
        GitWorkingTreesService.getInstance(project).deleteWorkingTree(project, tree, repository!!)
      }
    }
  }
}