// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.actions.branch.GitBranchActionsUtil.getRepositoriesForTopLevelActions
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.ui.branch.GitRefDialog

class GitCheckoutFromInputAction
  : DumbAwareAction(GitBundle.messagePointer("branches.checkout.tag.or.revision")) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val repositories = getRepositoriesForTopLevelActions(e)
    e.presentation.isEnabledAndVisible = !repositories.isEmpty()

    disableActionIfAnyRepositoryIsFresh(e, repositories, GitBundle.message("action.not.possible.in.fresh.repo.checkout"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val repositories = getRepositoriesForTopLevelActions(e)

    // TODO: on type check ref validity, on OK check ref existence.
    val dialog = GitRefDialog(project, repositories,
                              GitBundle.message("branches.checkout"),
                              GitBundle.message("branches.enter.reference.branch.tag.name.or.commit.hash"))
    if (dialog.showAndGet()) {
      val reference = dialog.reference
      GitBrancher.getInstance(project).checkout(reference, true, repositories, null)
    }
  }
}
