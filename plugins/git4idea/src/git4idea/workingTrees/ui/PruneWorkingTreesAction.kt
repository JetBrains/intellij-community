// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService

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
    val prunableWorktrees = repository.workingTreeHolder.getWorkingTrees().filter { it.isPrunable }.map { it.path.name }
    if (prunableWorktrees.isEmpty()) {
      return
    }

    val result = Messages.showYesNoDialog(GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.message",
                                                            prunableWorktrees.joinToString(", ")),
                                          GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.title"),
                                          GitBundle.message("Git.WorkingTrees.dialog.prune.worktree.yes.option"),
                                          CommonBundle.getCancelButtonText(),
                                          AllIcons.General.QuestionDialog)

    if (result == Messages.YES) {
      GitWorkingTreesService.getInstance(project).pruneWorkingTrees(project, repository)
    }
  }
}
