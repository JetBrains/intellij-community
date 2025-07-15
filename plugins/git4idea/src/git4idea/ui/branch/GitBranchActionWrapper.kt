// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository

internal class GitBranchActionWrapper(
  action: GitSingleRefAction<*>,
  private val branch: GitBranch,
  private val selectedRepository: GitRepository,
  private val repositories: List<GitRepository>,
) : AnActionWrapper(action), DataSnapshotProvider {
  override fun dataSnapshot(sink: DataSink) {
    sink[GitSingleRefActions.SELECTED_REF_DATA_KEY] = branch
    sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = repositories
    sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = selectedRepository
  }

  companion object {
    @JvmStatic
    fun tryWrap(action: AnAction, branch: GitBranch, selectedRepository: GitRepository, repositories: List<GitRepository>) =
      if (action is GitSingleRefAction<*>) GitBranchActionWrapper(action, branch, selectedRepository, repositories) else action
  }
}
