// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.actionSystem.*
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
    fun wrapActions(
      actions: Array<AnAction>,
      e: AnActionEvent?,
      branch: GitBranch,
      selectedRepository: GitRepository,
      repositories: List<GitRepository>,
    ): Array<AnAction> {
      val wrappedActions = mutableListOf<AnAction>()
      for (action in actions) {
        doWrapActions(wrappedActions, e, action, branch, selectedRepository, repositories)
      }
      return wrappedActions.toTypedArray()
    }

    private fun doWrapActions(
      result: MutableList<AnAction>,
      e: AnActionEvent?,
      action: AnAction,
      branch: GitBranch,
      selectedRepository: GitRepository,
      repositories: List<GitRepository>,
    ) {
      when (action) {
        is GitSingleRefAction<*> -> result.add(GitBranchActionWrapper(action, branch, selectedRepository, repositories))
        is ActionGroup -> {
          action.getChildren(e).forEach {
            doWrapActions(result, e, it, branch, selectedRepository, repositories)
          }
        }
        else -> result.add(action)
      }
    }
  }
}
