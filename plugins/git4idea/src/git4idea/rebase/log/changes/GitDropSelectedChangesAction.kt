// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.rebase.GitSingleCommitEditingAction
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.focusCommitWhenReady
import git4idea.rebase.log.notifySuccess
import kotlinx.coroutines.launch

internal class GitDropSelectedChangesAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.changes.action.operation.drop.name")
  )

  override fun update(e: AnActionEvent, commitEditingData: SingleCommitEditingData) {
    if (commitEditingData.selectedChanges.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val selectedCount = commitEditingData.selectedChanges.size
    val totalCount = commitEditingData.selectedCommitFullDetails.changes.size

    if (selectedCount == totalCount) {
      e.presentation.isEnabled = false
      e.presentation.description = GitBundle.message("rebase.log.changes.drop.action.description.all.changes.selected")
    }
  }

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val project = commitEditingData.project
    val repository = commitEditingData.repository
    val ui = commitEditingData.logUiEx

    GitDisposable.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("rebase.log.changes.drop.action.progress.indicator.title"), false) {
        val operationResult = GitDropSelectedChangesOperation(repository, commitEditingData.selectedCommit, commitEditingData.selectedChanges).execute()
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          ui?.focusCommitWhenReady(repository, operationResult.commitToFocus)
          operationResult.notifySuccess(
            GitBundle.message("rebase.log.changes.drop.action.notification.successful.title"),
            null,
            GitBundle.message("rebase.log.changes.drop.action.progress.indicator.undo.title"),
            GitBundle.message("rebase.log.changes.drop.action.notification.undo.not.allowed.title"),
            GitBundle.message("rebase.log.changes.drop.action.notification.undo.failed.title"),
            ui
          )
        }
      }
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.changes.drop.action.failure.title")
}