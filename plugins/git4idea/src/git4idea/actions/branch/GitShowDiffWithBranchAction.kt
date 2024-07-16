// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.actions.tag.GitSingleRefAction
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation
import git4idea.ui.branch.GitMultiRootBranchConfig

internal class GitShowDiffWithBranchAction
  : GitSingleRefAction<GitReference>(GitBundle.messagePointer("branches.show.diff.with.working.tree")) {

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    with(e.presentation) {
      isEnabledAndVisible = !GitMultiRootBranchConfig(repositories).diverged() || repositories.size == 1
      description = GitBundle.message("branches.compare.the.current.working.tree.with",
                                      getSelectedBranchFullPresentation(reference.name))
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    GitBrancher.getInstance(project).showDiffWithLocal(reference.name, repositories)
  }
}