// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitNotificationIdsHolder
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PruneWorkingTreesAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val repository = e.getData(CURRENT_REPOSITORY)
    e.presentation.isEnabled = repository != null && isEnabledFor(repository)
  }

  private fun isEnabledFor(repository: GitRepository): Boolean {
    return repository.workingTreeHolder.getWorkingTrees().any { it.isPrunable }
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val repository = e.getData(CURRENT_REPOSITORY) ?: return
    if (!isEnabledFor(repository)) {
      return
    }

    GitWorkingTreesService.getInstance(project).coroutineScope.launch {
      val prunableWorktrees = try {
        withBackgroundProgress(project, GitBundle.message("progress.title.getting.prunable.worktrees"), cancellable = true) {
          service<Git>().listWorktrees(repository).filter { it.isPrunable }.map { it.path.name }
        }
      }
      catch (e: VcsException) {
        VcsNotifier.getInstance(project).notifyError(
          GitNotificationIdsHolder.WORKING_TREES_LISTING_FAILED,
          GitBundle.message("Git.WorkingTrees.get.prunable.worktrees.failure.notification.title"),
          e.message,
          true
        )
        return@launch
      }

      if (!prunableWorktrees.isEmpty()) {
        val result = withContext(Dispatchers.UI) {
          Messages.showYesNoDialog(GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.message",
                                                     prunableWorktrees.joinToString(", ")),
                                   GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.title"),
                                   GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.yes.option"),
                                   CommonBundle.getCancelButtonText(),
                                   AllIcons.General.QuestionDialog)
        }

        if (result == Messages.YES) {
          prune(project, repository)
        }
      } else {
        VcsNotifier.getInstance(project).notifyMinorInfo(
          GitNotificationIdsHolder.WORKING_TREES_PRUNED,
          "",
          GitBundle.message("Git.WorkingTrees.prune.no.prunable.worktrees.message")
        )
      }
    }
  }


  private suspend fun prune(project: Project, repository: GitRepository) {
    val result = withBackgroundProgress(project, GitBundle.message("progress.title.pruning.worktrees"), cancellable = true) {
      service<Git>().pruneWorktrees(repository)
    }

    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(
        GitNotificationIdsHolder.WORKING_TREES_PRUNING_FAILED,
        GitBundle.message("Git.WorkingTrees.prune.worktrees.failure.notification.title"),
        result.errorOutputAsHtmlString,
        true
      )
      return
    }

    repository.workingTreeHolder.scheduleReload()
    VcsNotifier.getInstance(project).notifySuccess(
      GitNotificationIdsHolder.WORKING_TREES_PRUNED,
      "",
      GitBundle.message("Git.WorkingTrees.prune.worktree.success.message")
    )
  }
}
