// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.GitSingleCommitEditingAction
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.getOrLoadDetails
import git4idea.rebase.log.notifySuccess
import git4idea.repo.GitRepository
import kotlinx.coroutines.launch

internal class GitInMemoryRewordAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.reword.name")
  )

  override fun update(e: AnActionEvent, commitEditingData: SingleCommitEditingData) {
    if (!Registry.`is`("git.in.memory.reword.commit.enabled")) {
      e.presentation.isEnabledAndVisible = false
      return
    }
  }

  override fun checkNotMergeCommit(commitEditingData: SingleCommitEditingData): String? {
    val commit = commitEditingData.selectedCommit
    val repository = commitEditingData.repository
    if (commit.id.asString() == repository.currentRevision) {
      // allow amending merge commit
      return null
    }

    return super.checkNotMergeCommit(commitEditingData)
  }

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val details = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selection)
    val commit = details.first()
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = commit.fullMessage,
      title = GitBundle.message("rebase.log.reword.dialog.title"),
      dialogLabel = GitBundle.message(
        "rebase.log.reword.dialog.description.label",
        commit.id.toShortString(),
        getShortPresentation(commit.author)
      )
    )
    dialog.show { newMessage ->
      rewordInBackground(commitEditingData.project, commit, commitEditingData.repository, newMessage)
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.reword.action.failure.title")

  private fun rewordInBackground(project: Project, commit: VcsCommitMetadata, repository: GitRepository, newMessage: String) {
    GitDisposable.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
        val objectRepo = GitObjectRepository(repository)
        val operationResult = GitInMemoryRewordOperation(repository, objectRepo, commit, newMessage).execute()
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          operationResult.notifySuccess(
            GitBundle.message("rebase.log.reword.action.notification.successful.title"),
            null,
            GitBundle.message("rebase.log.reword.action.progress.indicator.undo.title"),
            GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.title"),
            GitBundle.message("rebase.log.reword.action.notification.undo.failed.title")
          )
          ChangeListManagerImpl.getInstanceImpl(project).replaceCommitMessage(commit.fullMessage, newMessage)
        }
      }
    }
  }

  override fun getProhibitedStateMessage(commitEditingData: SingleCommitEditingData, operation: String): String? {
    if (commitEditingData.repository.state == Repository.State.REBASING && commitEditingData.isHeadCommit) {
      return null
    }
    return super.getProhibitedStateMessage(commitEditingData, operation)
  }
}