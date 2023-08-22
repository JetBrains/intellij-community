// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

internal abstract class GitSingleCommitActionGroup() : ActionGroup(), DumbAware {
  constructor(actionText: @NlsActions.ActionText String, isPopup: Boolean) : this() {
    templatePresentation.text = actionText
    templatePresentation.isPopupGroup = isPopup
    templatePresentation.isHideGroupIfEmpty = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return AnAction.EMPTY_ARRAY

    val project = e.project
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    if (project == null || selection == null) {
      return AnAction.EMPTY_ARRAY
    }

    val commits = selection.commits
    if (commits.size != 1) return AnAction.EMPTY_ARRAY
    val commit = commits.first()

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(commit.root) ?: return AnAction.EMPTY_ARRAY

    return getChildren(e, project, repository, commit)
  }

  abstract fun getChildren(e: AnActionEvent, project: Project, repository: GitRepository, commit: CommitId): Array<AnAction>
}