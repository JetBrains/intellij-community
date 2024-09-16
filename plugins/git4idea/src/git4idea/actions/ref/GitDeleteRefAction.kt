// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.ref

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.isRemoteBranchProtected
import git4idea.repo.GitRepository

class GitDeleteRefAction : GitSingleRefAction<GitReference>(GitBundle.messagePointer("branches.action.delete")) {
  override fun isEnabledForRef(ref: GitReference, repositories: List<GitRepository>) = !isCurrentRefInAnyRepo(ref, repositories)

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, ref: GitReference) {
    if (ref is GitRemoteBranch) {
      e.presentation.isEnabled = !isRemoteBranchProtected(repositories, ref.name)
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    val brancher = GitBrancher.getInstance(project)
    when (reference) {
      is GitLocalBranch -> brancher.deleteBranch(reference.name, repositories.filter { it.currentBranch != reference })
      is GitRemoteBranch -> brancher.deleteRemoteBranch(reference.name, repositories)
      is GitTag -> brancher.deleteTag(reference.name, repositories)
    }
  }
}