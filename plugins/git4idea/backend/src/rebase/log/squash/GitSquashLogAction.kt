// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.squash

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.table.size
import git4idea.i18n.GitBundle
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
import git4idea.inMemory.rebase.log.RebaseEntriesSource
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitMultipleCommitEditingAction
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.executeInMemoryWithFallback
import git4idea.rebase.log.focusCommitWhenReady
import git4idea.rebase.log.getOrLoadDetails
import git4idea.rebase.log.notifySuccess
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class GitSquashLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    if (commitEditingData.selection.size < 2) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformedAfterChecks(scope: CoroutineScope, commitEditingData: MultipleCommitEditingData) {
    val selectedCommitDetails = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selection)
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = GitSquashedCommitsMessage.prettySquash(selectedCommitDetails.map(VcsCommitMetadata::getFullMessage)),
      title = GitBundle.message("rebase.log.squash.new.message.dialog.title"),
      dialogLabel = GitBundle.message("rebase.log.squash.new.message.dialog.label")
    )

    // The closure must not capture commitEditingData. Its selection holds the visible log graph, and the dialog outlives the action.
    val repository = commitEditingData.repository
    val logData = commitEditingData.logData
    val logUiEx = commitEditingData.logUiEx
    dialog.show { newMessage ->
      squashInBackground(scope, repository, logData, logUiEx, selectedCommitDetails, newMessage)
    }
  }

  private fun squashInBackground(
    scope: CoroutineScope,
    repository: GitRepository,
    logData: VcsLogData,
    logUiEx: VcsLogUiEx?,
    selectedCommitsDetails: List<VcsCommitMetadata>,
    newMessage: String,
  ) {
    scope.launch {
      val operationResult = executeSquashOperation(repository, logData, selectedCommitsDetails, newMessage)

      if (operationResult is GitCommitEditingOperationResult.Complete) {
        logUiEx?.focusCommitWhenReady(repository, operationResult.commitToFocus)
        operationResult.notifySuccess(
          GitBundle.message("rebase.log.squash.success.notification.title"),
          null,
          GitBundle.message("rebase.log.squash.undo.progress.title"),
          GitBundle.message("rebase.log.squash.undo.impossible.title"),
          GitBundle.message("rebase.log.squash.undo.failed.title")
        )
      }
    }
  }

  private suspend fun executeSquashOperation(
    repository: GitRepository,
    logData: VcsLogData,
    commitsToSquash: List<VcsCommitMetadata>,
    newMessage: String,
  ): GitCommitEditingOperationResult {
    return withBackgroundProgress(repository.project, GitBundle.message("rebase.log.squash.progress.indicator.title")) {
      executeInMemoryWithFallback(
        inMemoryOperation = {
          InMemoryRebaseOperations.squash(repository, commitsToSquash, newMessage, RebaseEntriesSource.LogData(logData))
        },
        fallbackOperation = {
          coroutineToIndicator {
            GitSquashOperation(repository).execute(commitsToSquash, newMessage)
          }
        }
      )
    }
  }

  override fun getFailureTitle() = GitBundle.message("rebase.log.squash.action.failure.title")
}
