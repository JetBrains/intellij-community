// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitFreezingProcess
import org.jetbrains.annotations.Nls

internal abstract class GitAbortOperationAction(repositoryState: Repository.State,
                                                final override val operationName: @Nls String,
                                                private val gitCommand: GitCommand)
  : GitOperationActionBase(repositoryState) {

  private val operationNameCapitalised = StringUtil.capitalizeWords(operationName, true)

  protected abstract val notificationSuccessDisplayId: String
  protected abstract val notificationErrorDisplayId: String

  class Merge : GitAbortOperationAction(Repository.State.MERGING, GitBundle.message("abort.operation.merge.name"), GitCommand.MERGE) {
    override val notificationSuccessDisplayId = "git.merge.abort.success"
    override val notificationErrorDisplayId = "git.merge.abort.failed"
  }

  class CherryPick : GitAbortOperationAction(Repository.State.GRAFTING, GitBundle.message("abort.operation.cherry.pick.name"), GitCommand.CHERRY_PICK) {
    override val notificationSuccessDisplayId = "git.cherry.pick.abort.success"
    override val notificationErrorDisplayId = "git.cherry.pick.abort.failed"
  }

  class Revert : GitAbortOperationAction(Repository.State.REVERTING, GitBundle.message("abort.operation.revert.name"), GitCommand.REVERT) {
    override val notificationSuccessDisplayId = "git.revert.abort.success"
    override val notificationErrorDisplayId = "git.revert.abort.failed"
  }

  override fun performInBackground(repository: GitRepository) {
    if (!confirmAbort(repository)) return

    runBackgroundableTask(GitBundle.message("abort.operation.progress.title", operationNameCapitalised), repository.project) { indicator ->
      doAbort(repository, indicator)
    }
  }

  private fun confirmAbort(repository: GitRepository): Boolean {
    var title = GitBundle.message("abort.operation.dialog.title", operationNameCapitalised)
    var message = GitBundle.message("abort.operation.dialog.msg", operationName, GitUtil.mention(repository))
    if (Messages.canShowMacSheetPanel()) {
      title = message
      message = ""
    }
    return DialogManager.showOkCancelDialog(repository.project, message, title, GitBundle.message("abort"), CommonBundle.getCancelButtonText(),
                                            Messages.getQuestionIcon()) == Messages.OK
  }

  private fun doAbort(repository: GitRepository, indicator: ProgressIndicator) {
    val project = repository.project
    GitFreezingProcess(project, GitBundle.message("abort")) {
      DvcsUtil.workingTreeChangeStarted(project, GitBundle.message("abort")).use {
        indicator.text2 = GitBundle.message("abort.operation.indicator.text", gitCommand.name(), GitUtil.mention(repository))

        val startHash = GitUtil.getHead(repository)
        val stagedChanges = GitChangeUtils.getStagedChanges(project, repository.root)

        val handler = GitLineHandler(project, repository.root, gitCommand)
        handler.addParameters("--abort")
        val result = Git.getInstance().runCommand(handler)

        if (!result.success()) {
          VcsNotifier.getInstance(project).notifyError(notificationErrorDisplayId, GitBundle.message("abort.operation.failed", operationNameCapitalised), result.errorOutputAsHtmlString, true)
        }
        else {
          VcsNotifier.getInstance(project).notifySuccess(notificationSuccessDisplayId, "", GitBundle.message("abort.operation.succeeded", operationNameCapitalised))

          GitUtil.updateAndRefreshChangedVfs(repository, startHash)
          RefreshVFsSynchronously.refresh(stagedChanges, true)
        }
      }
    }.execute()
  }
}
