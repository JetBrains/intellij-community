// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListData
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import git4idea.DialogManager
import git4idea.GitApplyChangesNotification
import git4idea.GitActivity
import git4idea.GitNotificationIdsHolder.Companion.CHERRY_PICK_ABORT_FAILED
import git4idea.GitNotificationIdsHolder.Companion.CHERRY_PICK_ABORT_SUCCESS
import git4idea.GitNotificationIdsHolder.Companion.MERGE_ABORT_FAILED
import git4idea.GitNotificationIdsHolder.Companion.MERGE_ABORT_SUCCESS
import git4idea.GitNotificationIdsHolder.Companion.REVERT_ABORT_FAILED
import git4idea.GitNotificationIdsHolder.Companion.REVERT_ABORT_SUCCESS
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitFreezingProcess
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal abstract class GitAbortOperationAction(
  private val repositoryState: Repository.State,
  final override val operationName: @Nls String,
  private val gitCommand: GitCommand,
)
  : GitOperationActionBase(repositoryState) {

  private val operationNameCapitalised = StringUtil.capitalizeWords(operationName, true)

  protected abstract val notificationSuccessDisplayId: String
  protected abstract val notificationErrorDisplayId: String

  class Merge : GitAbortOperationAction(Repository.State.MERGING, GitBundle.message("abort.operation.merge.name"), GitCommand.MERGE) {
    override val notificationSuccessDisplayId = MERGE_ABORT_SUCCESS
    override val notificationErrorDisplayId = MERGE_ABORT_FAILED
  }

  class CherryPick : GitAbortOperationAction(Repository.State.GRAFTING, GitBundle.message("abort.operation.cherry.pick.name"), GitCommand.CHERRY_PICK) {
    override val notificationSuccessDisplayId = CHERRY_PICK_ABORT_SUCCESS
    override val notificationErrorDisplayId = CHERRY_PICK_ABORT_FAILED
  }

  class Revert : GitAbortOperationAction(Repository.State.REVERTING, GitBundle.message("abort.operation.revert.name"), GitCommand.REVERT) {
    override val notificationSuccessDisplayId = REVERT_ABORT_SUCCESS
    override val notificationErrorDisplayId = REVERT_ABORT_FAILED
  }

  final override fun getMainToolbarIcon(): Icon = AllIcons.Vcs.Abort

  override fun performInBackground(repository: GitRepository): Boolean {
    if (!confirmAbort(repository)) return false

    runBackgroundableTask(GitBundle.message("abort.operation.progress.title", operationNameCapitalised), repository.project) { indicator ->
      doAbort(repository, indicator)
    }

    GitApplyChangesNotification.expireAll<GitApplyChangesNotification.ExpireAfterAbort>(repository.project)

    return true
  }

  private fun confirmAbort(repository: GitRepository): Boolean {
    val title = GitBundle.message("abort.operation.dialog.title", operationNameCapitalised)
    val message = GitBundle.message("abort.operation.dialog.msg", operationName, GitUtil.mention(repository))
    return DialogManager.showOkCancelDialog(repository.project, message, title, GitBundle.message("abort"), CommonBundle.getCancelButtonText(),
                                            Messages.getQuestionIcon()) == Messages.OK
  }

  private fun doAbort(repository: GitRepository, indicator: ProgressIndicator) {
    val project = repository.project
    GitFreezingProcess(project, GitBundle.message("abort")) {
      DvcsUtil.workingTreeChangeStarted(project, GitBundle.message("activity.name.abort.command", operationName), GitActivity.Abort).use {
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

          if (repositoryState == Repository.State.GRAFTING) {
            // cleanup after GitApplyChangesProcess
            val changeListManager = ChangeListManagerEx.getInstanceEx(project)
            for (list in changeListManager.changeLists) {
              val isAutomatic = (list.data as? ChangeListData)?.automatic == true
              if (isAutomatic) {
                changeListManager.editChangeListData(list.name, null)
              }
            }
          }
        }
      }
    }.execute()
  }
}
