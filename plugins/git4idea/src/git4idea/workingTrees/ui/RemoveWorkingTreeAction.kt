// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitNotificationIdsHolder
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    return project != null && repository != null && !trees.isNullOrEmpty() && trees.all { !it.isCurrent && !it.isMain }
  }

  override fun actionPerformed(e: AnActionEvent) {
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

    if (result != Messages.YES) return
    GitWorkingTreesService.getInstance(project).coroutineScope.launch(Dispatchers.IO) {
      for (tree in data) {
        delete(project, tree, repository!!)
      }
    }
  }

  private suspend fun delete(project: Project, tree: GitWorkingTree, repository: GitRepository) {
    val commandResult = withBackgroundProgress(project, GitBundle.message("progress.title.deleting.worktree"), cancellable = true) {
      service<Git>().deleteWorkingTree(project, tree)
    }
    if (commandResult.success()) {
      repository.workingTreeHolder.reload()
      VcsNotifier.getInstance(project).notifySuccess(GitNotificationIdsHolder.WORKING_TREE_DELETED,
                                                     "",
                                                     GitBundle.message("Git.WorkingTrees.delete.worktree.success.message", tree.path.presentableUrl))
    }
    else {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKING_TREE_COULD_NOT_DELETE,
                                                   GitBundle.message("Git.WorkingTrees.delete.worktrees.failure.notification.title"),
                                                   commandResult.errorOutputAsHtmlString,
                                                   true)
    }
  }
}