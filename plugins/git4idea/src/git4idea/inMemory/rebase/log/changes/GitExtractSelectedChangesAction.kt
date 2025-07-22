// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.GitSingleCommitEditingAction
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.focusCommitAfterLogUpdate
import git4idea.rebase.log.getOrLoadSingleCommitDetails
import git4idea.rebase.log.notifySuccess
import kotlinx.coroutines.launch

internal class GitExtractSelectedChangesAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("in.memory.rebase.log.changes.action.operation.extract.name")
  )

  override fun update(e: AnActionEvent, commitEditingData: SingleCommitEditingData) {
    if (!Registry.`is`("git.in.memory.extract.selected.changes.enabled") || commitEditingData.selectedChanges.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val selectedCount = commitEditingData.selectedChanges.size
    val totalCount = commitEditingData.selectedCommitFullDetails.changes.size

    if (selectedCount == totalCount) {
      e.presentation.isEnabled = false
      e.presentation.description = GitBundle.message("in.memory.rebase.log.changes.extract.action.description.all.changes.selected")
    }
  }

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val project = commitEditingData.project
    val repository = commitEditingData.repository
    val commit = getOrLoadSingleCommitDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selection)
    val changes = commitEditingData.selectedChanges
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = commit.fullMessage,
      title = GitBundle.message("in.memory.rebase.log.changes.extract.dialog.title"),
      dialogLabel = GitBundle.message(
        "in.memory.rebase.log.changes.extract.dialog.description.label",
        commit.id.toShortString(),
        getShortPresentation(commit.author)
      )
    )
    val ui = commitEditingData.logUiEx

    dialog.show { newMessage ->
      GitDisposable.getInstance(project).coroutineScope.launch {
        val operationResult = withBackgroundProgress(project, GitBundle.message("in.memory.rebase.log.change.extract.action.progress.indicator.title")) {
          val objectRepo = GitObjectRepository(repository)
          GitExtractSelectedChangesOperation(objectRepo, commit, newMessage, changes).execute()
        }
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          ui?.focusCommitAfterLogUpdate(repository, operationResult.commitToFocus)
          operationResult.notifySuccess(
            GitBundle.message("rebase.in.memory.log.changes.extract.action.notification.successful.title"),
            null,
            GitBundle.message("rebase.in.memory.log.changes.extract.action.progress.indicator.undo.title"),
            GitBundle.message("rebase.in.memory.log.changes.extract.action.notification.undo.not.allowed.title"),
            GitBundle.message("rebase.in.memory.log.changes.extract.action.notification.undo.failed.title")
          )
        }
      }
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("in.memory.rebase.log.changes.extract.action.failure.title")
}