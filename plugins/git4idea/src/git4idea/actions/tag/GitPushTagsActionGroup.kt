// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.GitTag
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.VisibleForTesting

internal class GitPushTagsActionGroup : ActionGroup(GitBundle.messagePointer("action.Git.Push.Tag.text"), false) {
  override fun update(e: AnActionEvent) {
    val tag = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY) as? GitTag
    val repositories = e.getData(GitBranchActionsDataKeys.AFFECTED_REPOSITORIES)
    if (tag == null || repositories == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val actionsNumber = repositories.sumOf { repo -> repo.remotes.size }
    e.presentation.isPopupGroup = actionsNumber >= MAX_ACTIONS_UNTIL_POPUP
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val repositories = e?.getData(GitBranchActionsDataKeys.AFFECTED_REPOSITORIES) ?: return emptyArray()

    val action = ActionManager.getInstance().getAction(GitPushTagAction.ACTION_ID)

    return repositories.flatMap { repo ->
      repo.remotes.map<GitRemote, GitPushTagActionWrapper> { remote ->
        GitPushTagActionWrapper(action, repo, remote)
      }
    }.toTypedArray()
  }

  companion object {
    val REMOTE_IN_REPOSITORY_KEY = DataKey.create<GitRemote>("Git.Remote.In.Repository")

    private const val MAX_ACTIONS_UNTIL_POPUP = 6
  }
}

@VisibleForTesting
class GitPushTagActionWrapper(
  action: AnAction,
  private val repository: GitRepository,
  private val remote: GitRemote,
) : AnActionWrapper(action), DataSnapshotProvider {
  override fun dataSnapshot(sink: DataSink) {
    sink[GitPushTagsActionGroup.REMOTE_IN_REPOSITORY_KEY] = remote
    sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = repository
  }
}