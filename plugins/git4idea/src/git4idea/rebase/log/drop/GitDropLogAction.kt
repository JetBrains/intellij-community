// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.drop

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitMultipleCommitEditingAction
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult
import git4idea.rebase.log.getOrLoadDetails
import git4idea.rebase.log.notifySuccess

internal class GitDropLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    e.presentation.text = GitBundle.message("rebase.log.drop.action.custom.text", commitEditingData.selectedCommitList.size)
  }

  override fun actionPerformedAfterChecks(commitEditingData: MultipleCommitEditingData) {
    val project = commitEditingData.project
    val commitDetails = getOrLoadDetails(project, commitEditingData.logData, commitEditingData.selectedCommitList)
    object : Task.Backgroundable(project, GitBundle.message("rebase.log.drop.progress.indicator.title", commitDetails.size)) {
      override fun run(indicator: ProgressIndicator) {
        val operationResult = GitDropOperation(commitEditingData.repository).execute(commitDetails)
        if (operationResult is GitMultipleCommitEditingOperationResult.Complete) {
          operationResult.notifySuccess(
            GitBundle.message("rebase.log.drop.success.notification.title", commitDetails.size),
            GitBundle.getString("rebase.log.drop.undo.progress.title"),
            GitBundle.getString("rebase.log.drop.undo.impossible.title"),
            GitBundle.getString("rebase.log.drop.undo.failed.title")
          )
        }
      }
    }.queue()
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.drop.action.failure.title")
}