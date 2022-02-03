// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.*

internal class GitMergeBranchAction
  : GitSingleBranchAction() {

  override val disabledForCurrent = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      text = GitBundle.message("branches.merge.into",
                               getSelectedBranchTruncatedPresentation(project, branch.name),
                               getCurrentBranchTruncatedPresentation(project, repositories))
      description = GitBundle.message("branches.merge.into",
                                      getSelectedBranchFullPresentation(branch.name),
                                      getCurrentBranchFullPresentation(project, repositories))
      addTooltipText(this, GitBundle.message("branches.merge.into",
                                             getSelectedBranchFullPresentation(branch.name),
                                             getCurrentBranchFullPresentation(project, repositories)))
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    GitBrancher.getInstance(project).merge(branch.name, deleteOnMerge(branch), repositories)
  }

  //TODO: remove hardcoded branch name
  private fun deleteOnMerge(branch: GitBranch): GitBrancher.DeleteOnMergeOption {
    return if (!branch.isRemote && branch.name != GitRefManager.MASTER) { // NON-NLS
      GitBrancher.DeleteOnMergeOption.PROPOSE
    }
    else GitBrancher.DeleteOnMergeOption.NOTHING
  }
}