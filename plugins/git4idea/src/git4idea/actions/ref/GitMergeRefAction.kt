// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.ref

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.branch.GitBrancher
import git4idea.config.GitSharedSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions

internal class GitMergeRefAction : GitSingleRefAction<GitReference>(GitBundle.messagePointer("branches.merge.into.current")) {

  override fun isEnabledForRef(ref: GitReference, repositories: List<GitRepository>) = !isCurrentRefInAnyRepo(ref, repositories)

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    GitBrancher.getInstance(project).merge(reference, deleteOnMerge(reference, project), repositories)
  }

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    with(e.presentation) {
      text = GitBundle.message("branches.merge.into",
                               GitBranchPopupActions.getSelectedBranchTruncatedPresentation(project, reference.name),
                               GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))
      description = GitBundle.message("branches.merge.into",
                                      GitBranchPopupActions.getSelectedBranchFullPresentation(reference.name),
                                      GitBranchPopupActions.getCurrentBranchFullPresentation(project, repositories))
      GitBranchPopupActions.addTooltipText(this, GitBundle.message("branches.merge.into",
                                                                   GitBranchPopupActions.getSelectedBranchFullPresentation(reference.name),
                                                                   GitBranchPopupActions.getCurrentBranchFullPresentation(project,
                                                                                                                          repositories)))
    }
  }

  private fun deleteOnMerge(reference: GitReference, project: Project): GitBrancher.DeleteOnMergeOption {
    return if (reference is GitBranch && !reference.isRemote && !GitSharedSettings.getInstance(project).isBranchProtected(reference.name)) {
      GitBrancher.DeleteOnMergeOption.PROPOSE
    }
    else GitBrancher.DeleteOnMergeOption.NOTHING
  }
}