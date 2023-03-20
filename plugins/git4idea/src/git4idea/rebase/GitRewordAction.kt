// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.getOrLoadDetails
import git4idea.rebase.log.notifySuccess
import git4idea.repo.GitRepository

internal class GitRewordAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.reword.name")
  )

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
    object : Task.Backgroundable(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        val operationResult = GitRewordOperation(repository, commit, newMessage).execute()
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          operationResult.notifySuccess(
            GitBundle.message("rebase.log.reword.action.notification.successful.title"),
            GitBundle.message("rebase.log.reword.action.progress.indicator.undo.title"),
            GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.title"),
            GitBundle.message("rebase.log.reword.action.notification.undo.failed.title")
          )
          ChangeListManagerImpl.getInstanceImpl(project).replaceCommitMessage(commit.fullMessage, newMessage)
        }
      }
    }.queue()
  }

  override fun getProhibitedStateMessage(commitEditingData: SingleCommitEditingData, operation: String): String? {
    if (commitEditingData.repository.state == Repository.State.REBASING && commitEditingData.isHeadCommit) {
      return null
    }
    return super.getProhibitedStateMessage(commitEditingData, operation)
  }
}