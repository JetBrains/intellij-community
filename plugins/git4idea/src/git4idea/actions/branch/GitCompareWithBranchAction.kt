// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.*

internal class GitCompareWithBranchAction
  : GitSingleBranchAction() {

  override val disabledForCurrent = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      text = GitBundle.message("branches.compare.with.branch",
                               getCurrentBranchTruncatedPresentation(project, repositories))
      description = GitBundle.message("branches.show.commits.in",
                                      getSelectedBranchFullPresentation(branch.name),
                                      getCurrentBranchFullPresentation(project, repositories))
      addTooltipText(this, description)
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    FileDocumentManager.getInstance().saveAllDocuments()
    GitBrancher.getInstance(project).compare(branch.name, repositories)
  }
}