// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation
import git4idea.ui.branch.GitMultiRootBranchConfig

class GitShowDiffWithBranchAction
  : GitSingleBranchAction(GitBundle.messagePointer("branches.show.diff.with.working.tree")) {

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      isEnabledAndVisible = !GitMultiRootBranchConfig(repositories).diverged()
      description = GitBundle.message("branches.compare.the.current.working.tree.with",
                                      getSelectedBranchFullPresentation(branch.name))
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    GitBrancher.getInstance(project).showDiffWithLocal(branch.name, repositories)
  }
}