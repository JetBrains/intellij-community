// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.actions.GitSingleRefActions
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.actions.ref.GitSingleRefAction
import git4idea.i18n.GitBundle

/**
 * An action group that is supposed to be used as a sub-group of the branch actions group
 * to show actions for the tracked branch of the currently selected local branch.
 */
internal class GitTrackedBranchActionGroup : ActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val available = Registry.get("git.tracked.branch.actions.in.widget").asBoolean()
    // cannot be placed on a toolbar in the current implementation, as `getChildren` creates new instances on each call
    if (!available || e.isFromActionToolbar) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val trackedBranch = findTrackedBranch(e.dataContext)
    if (trackedBranch == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.text = GitBundle.message("group.Git.Branch.Tracked.name.withBranch", trackedBranch.nameForLocalOperations)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return EMPTY_ARRAY
    val trackedBranch = findTrackedBranch(e.dataContext) ?: return EMPTY_ARRAY

    return GitSingleRefActions.getSingleRefActionGroup().getChildren(e)
      .mapNotNull {
        when (it) {
          is GitSingleRefAction<*> -> wrapToOverrideSelectedRefWithTracked(it, trackedBranch)
          is Separator -> it
          else -> null
        }
      }.toTypedArray()
  }

}

private fun wrapToOverrideSelectedRefWithTracked(action: AnAction, trackedBranch: GitStandardRemoteBranch): AnActionWrapper =
  object : AnActionWrapper(action), DataSnapshotProvider {
    override fun dataSnapshot(sink: DataSink) {
      sink[GitSingleRefActions.SELECTED_REF_DATA_KEY] = trackedBranch
    }
  }

private fun findTrackedBranch(ctx: DataContext): GitStandardRemoteBranch? {
  val repo = ctx[GitBranchesPopupKeys.SELECTED_REPOSITORY]
             ?: ctx[GitBranchesPopupKeys.AFFECTED_REPOSITORIES]?.singleOrNull()
             ?: return null
  val localBranch = ctx[GitSingleRefActions.SELECTED_REF_DATA_KEY] as? GitStandardLocalBranch ?: return null
  return repo.state.getTrackingInfo(localBranch)
}