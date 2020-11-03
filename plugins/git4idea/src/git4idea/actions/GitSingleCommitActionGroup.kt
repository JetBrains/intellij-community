// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

internal abstract class GitSingleCommitActionGroup() : ActionGroup(), DumbAware {
  constructor(actionText: @NlsActions.ActionText String, isPopup: Boolean) : this() {
    templatePresentation.text = actionText
    setPopup(isPopup)
  }

  override fun hideIfNoVisibleChildren(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return AnAction.EMPTY_ARRAY

    val project = e.project
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    if (project == null || log == null) {
      return AnAction.EMPTY_ARRAY
    }

    val commits = log.selectedCommits
    if (commits.size != 1) return AnAction.EMPTY_ARRAY
    val commit = commits.first()

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(commit.root) ?: return AnAction.EMPTY_ARRAY

    return getChildren(e, project, log, repository, commit)
  }

  abstract fun getChildren(e: AnActionEvent, project: Project, log: VcsLog, repository: GitRepository, commit: CommitId): Array<AnAction>
}