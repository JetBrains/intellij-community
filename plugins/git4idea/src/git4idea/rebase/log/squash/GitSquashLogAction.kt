// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.squash

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitMultipleCommitEditingAction
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.getOrLoadDetails

internal class GitSquashLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    if (commitEditingData.selectedCommitList.size < 2) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformedAfterChecks(commitEditingData: MultipleCommitEditingData) {
    val selectedCommitDetails = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selectedCommitList)
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = selectedCommitDetails.joinToString("\n".repeat(3)) { it.fullMessage },
      title = GitBundle.getString("rebase.log.squash.new.message.dialog.title"),
      dialogLabel = GitBundle.getString("rebase.log.squash.new.message.dialog.label")
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
    object : Task.Backgroundable(commitEditingData.project, GitBundle.getString("rebase.log.squash.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        GitSquashOperation(commitEditingData.repository).execute(selectedCommitsDetails, newMessage)
      }
    }.queue()
  }

  override fun getFailureTitle() = GitBundle.getString("rebase.log.squash.action.failure.title")
}