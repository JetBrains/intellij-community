// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.squash

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import git4idea.rebase.log.*

internal class GitSquashLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    if (commitEditingData.selection.size < 2) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformedAfterChecks(commitEditingData: MultipleCommitEditingData) {
    val selectedCommitDetails = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selection)
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = selectedCommitDetails.joinToString("\n".repeat(3)) { it.fullMessage },
      title = GitBundle.message("rebase.log.squash.new.message.dialog.title"),
      dialogLabel = GitBundle.message("rebase.log.squash.new.message.dialog.label")
    )
    dialog.show { newMessage ->
      squashInBackground(commitEditingData, selectedCommitDetails, newMessage)
    }
  }

  private fun squashInBackground(
    commitEditingData: MultipleCommitEditingData,
    selectedCommitsDetails: List<VcsCommitMetadata>,
    newMessage: String
  ) {
    object : Task.Backgroundable(commitEditingData.project, GitBundle.message("rebase.log.squash.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        val operationResult = GitSquashOperation(commitEditingData.repository).execute(selectedCommitsDetails, newMessage)
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          operationResult.notifySuccess(
            GitBundle.message("rebase.log.squash.success.notification.title"),
            GitBundle.message("rebase.log.squash.undo.progress.title"),
            GitBundle.message("rebase.log.squash.undo.impossible.title"),
            GitBundle.message("rebase.log.squash.undo.failed.title")
          )
        }
      }
    }.queue()
  }

  override fun getFailureTitle() = GitBundle.message("rebase.log.squash.action.failure.title")
}