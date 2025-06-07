// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.drop

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.applyIf
import com.intellij.vcs.log.ui.table.size
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitMultipleCommitEditingAction
import git4idea.rebase.log.getOrLoadDetails
import git4idea.rebase.log.notifySuccess
import git4idea.ui.branch.GitBranchPopupActions

internal class GitDropLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    e.presentation.text = GitBundle.message("rebase.log.drop.action.custom.text", commitEditingData.selection.size)
  }

  override fun actionPerformedAfterChecks(commitEditingData: MultipleCommitEditingData) {
    val project = commitEditingData.project

    val canDrop = !service<GitVcsApplicationSettings>().isShowDropCommitDialog || askForConfirmation(project, commitEditingData)
    if (!canDrop) return

    val commitDetails = getOrLoadDetails(project, commitEditingData.logData, commitEditingData.selection)
    object : Task.Backgroundable(project, GitBundle.message("rebase.log.drop.progress.indicator.title", commitDetails.size)) {
      override fun run(indicator: ProgressIndicator) {
        val operationResult = GitDropOperation(commitEditingData.repository).execute(commitDetails)
        if (operationResult is GitCommitEditingOperationResult.Complete) {
          val notificationTitle = GitBundle.message("rebase.log.drop.success.notification.title", commitDetails.size)
          val notificationContent = HtmlBuilder().appendWithSeparators(HtmlChunk.br(), commitDetails.take(MAX_COMMITS_IN_NOTIFICATION).map {
            HtmlChunk.text("\"${StringUtil.shortenTextWithEllipsis(it.subject, 40, 0)}\"")
          }).applyIf(commitDetails.size > MAX_COMMITS_IN_NOTIFICATION) { this.br().append("...") }.toString()

          operationResult.notifySuccess(
            notificationTitle,
            notificationContent,
            GitBundle.message("rebase.log.drop.undo.progress.title"),
            GitBundle.message("rebase.log.drop.undo.impossible.title"),
            GitBundle.message("rebase.log.drop.undo.failed.title")
          )
        }
      }
    }.queue()
  }

  private fun askForConfirmation(project: Project, data: MultipleCommitEditingData): Boolean {
    val commitsCount = data.selection.size
    val branchName = data.repository.currentBranch?.name!!
    val branchPresentation = GitBranchPopupActions.getSelectedBranchFullPresentation(branchName)

    return MessageDialogBuilder
      .okCancel(
        GitBundle.message("rebase.log.drop.action.confirmation.title", commitsCount),
        GitBundle.message("rebase.log.drop.action.confirmation.message", commitsCount, branchPresentation)
      )
      .asWarning()
      .doNotAsk(object : com.intellij.openapi.ui.DoNotAskOption.Adapter() {
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          service<GitVcsApplicationSettings>().isShowDropCommitDialog = !isSelected
        }
      })
      .help(DROP_COMMIT_HELP_ID)
      .ask(project)
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.drop.action.failure.title")

  companion object {
    private const val MAX_COMMITS_IN_NOTIFICATION = 10
    const val DROP_COMMIT_HELP_ID = "reference.VersionControl.Git.DropCommit"
  }
}