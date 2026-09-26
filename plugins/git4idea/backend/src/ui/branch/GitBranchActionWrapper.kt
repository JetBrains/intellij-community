// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.vcs.git.actions.GitSingleRefActions
import com.intellij.vcs.git.actions.branch.GitBranchActionToBeWrapped
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.repo.GitRepository


internal class GitBranchActionWrapperGroup(
  actionGroup: ActionGroup,
  private val branch: GitBranch,
  private val selectedRepository: GitRepository,
  private val repositories: List<GitRepository>,
) : ActionGroupWrapper(actionGroup), DataSnapshotProvider {
  override fun dataSnapshot(sink: DataSink) = dataSnapshot(sink, branch, repositories, selectedRepository)
}

internal class GitBranchActionWrapper private constructor(
  action: AnAction,
  private val branch: GitBranch,
  private val selectedRepository: GitRepository,
  private val repositories: List<GitRepository>,
) : AnActionWrapper(action), DataSnapshotProvider {
  override fun dataSnapshot(sink: DataSink) = dataSnapshot(sink, branch, repositories, selectedRepository)

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
        is GitBranchActionToBeWrapped -> {
          if (action is ActionGroup) {
            result.add(GitBranchActionWrapperGroup(action, branch, selectedRepository, repositories))
          }
          else {
            result.add(GitBranchActionWrapper(action, branch, selectedRepository, repositories))
          }
        }
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

private fun dataSnapshot(sink: DataSink, branch: GitBranch, repositories: List<GitRepository>, repository: GitRepository) {
  sink[GitSingleRefActions.SELECTED_REF_DATA_KEY] = branch
  sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = repositories
  sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = repository
}