// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.vcs.log.VcsShortCommitDetails
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

abstract class GitAutoSquashCommitAction : GitCommitEditingAction() {

  override fun actionPerformedAfterChecks(e: AnActionEvent) {
    val commit = getSelectedCommit(e)
    val project = e.project!!

    val changeList = ChangeListManager.getInstance(project).defaultChangeList
    val repository = getRepository(e)

    val gitRepositoryManager = GitRepositoryManager.getInstance(project)

    val changes = changeList.changes.filter {
      gitRepositoryManager.getRepositoryForFileQuick(ChangesUtil.getFilePath(it)) == repository
    }

    val executors = repository.vcs.commitExecutors +
                    if (getProhibitedStateMessage(e, GitBundle.getString("rebase.log.action.operation.rebase.name")) == null) {
                      listOf(GitRebaseAfterCommitExecutor(project, repository, commit.id.asString() + "~"))
                    }
                    else {
                      listOf()
                    }
    CommitChangeListDialog.commitChanges(project,
                                         changes,
                                         changes,
                                         changeList,
                                         executors,
                                         true,
                                         null,
                                         getCommitMessage(commit),
                                         null,
                                         true)
  }

  protected abstract fun getCommitMessage(commit: VcsShortCommitDetails): String

  class GitRebaseAfterCommitExecutor(val project: Project, val repository: GitRepository, val hash: String) : CommitExecutor {
    override fun getActionText(): String = GitBundle.getString("commit.action.commit.and.rebase.text")
    override fun createCommitSession(commitContext: CommitContext): CommitSession = CommitSession.VCS_COMMIT
    override fun supportsPartialCommit() = true
  }
}