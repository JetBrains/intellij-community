// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.addTooltipText
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation
import git4idea.ui.branch.createOrCheckoutNewBranch

class GitCheckoutAsNewBranch
  : GitSingleBranchAction() {

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      val branchName = branch.name
      text = GitBundle.message("branches.new.branch.from.branch", getSelectedBranchFullPresentation(branchName))
      description = GitBundle.message("branches.new.branch.from.branch.description", getSelectedBranchFullPresentation(branchName))

      addTooltipText(this, description)
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    createOrCheckoutNewBranch(project, repositories, "${branch.name}^0",
                              GitBundle.message("action.Git.New.Branch.dialog.title", branch.name))
  }
}