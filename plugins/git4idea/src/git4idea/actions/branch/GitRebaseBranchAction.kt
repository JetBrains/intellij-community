// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.*

class GitRebaseBranchAction : GitSingleBranchAction() {

  override val disabledForCurrent = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      text = GitBundle.message(
        "branches.rebase.onto",
        getCurrentBranchTruncatedPresentation(project, repositories),
        getSelectedBranchTruncatedPresentation(project, branch.name))

      val isOnBranch = repositories.all { it.isOnBranch }
      isEnabled = isOnBranch
      description = if (isOnBranch) GitBundle.message("branches.rebase.onto",
                                                      getCurrentBranchFullPresentation(project, repositories),
                                                      getSelectedBranchFullPresentation(branch.name))
      else GitBundle.message("branches.rebase.is.not.possible.in.the.detached.head.state")

      addTooltipText(this, description)
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    GitBrancher.getInstance(project).rebase(repositories, branch.name)
  }
}