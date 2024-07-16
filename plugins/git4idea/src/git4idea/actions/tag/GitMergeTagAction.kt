// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitTag
import git4idea.branch.GitBrancher
import git4idea.branch.GitBrancher.DeleteOnMergeOption
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions

internal class GitMergeTagAction : GitSingleTagAction(GitBundle.messagePointer("branches.merge.into.current")) {
  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitTag) {
    super.updateIfEnabledAndVisible(e, project, repositories, reference)
    val selectedTagName = reference.name
    val presentation = e.presentation
    val description = GitBundle.message("branches.merge.into",
                                        GitBranchPopupActions.getSelectedBranchFullPresentation(selectedTagName),
                                        GitBranchPopupActions.getCurrentBranchFullPresentation(project, repositories))
    presentation.setDescription(description)
    GitBranchPopupActions.addTooltipText(presentation, description)

    val name = GitBundle.message("branches.merge.into",
                                 GitBranchPopupActions.getSelectedBranchTruncatedPresentation(project, selectedTagName),
                                 GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))
    presentation.setText(name)
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitTag) {
    GitBrancher.getInstance(project).merge(reference, DeleteOnMergeOption.NOTHING, repositories)
  }
}