// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotificationIdsHolder
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitApplyChangesNotification
import git4idea.GitApplyChangesProcess
import git4idea.GitDisposable
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.stash.GitChangesSaver
import git4idea.util.LocalChangesWouldBeOverwrittenHelper
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

internal class GitApplyChangesLocalChangesDetectedNotification(
  operationName: @Nls String,
  failedOnCommit: VcsCommitMetadata?,
  successfulCommits: List<VcsCommitMetadata>,
  repository: GitRepository,
  retryAction: (GitChangesSaver) -> Unit,
) : GitApplyChangesNotification(
  VcsNotifier.importantNotification().displayId,
  GitBundle.message("apply.changes.operation.failed", operationName.capitalize()),
  getDescription(operationName, repository, failedOnCommit, successfulCommits),
  NotificationType.ERROR,
) {
  init {
    val project = repository.project
    val affectedPaths = repository.getStagingAreaHolder().allRecords.map { it.path }
    val localChanges =
      GitUtil.findLocalChangesForPaths(project, repository.getRoot(), affectedPaths.map(FilePath::getPath), false)

    setDisplayId(GitNotificationIdsHolder.Companion.APPLY_CHANGES_LOCAL_CHANGES_DETECTED)

    addAction(NotificationAction.createSimple(IdeBundle.messagePointer("action.show.files")) {
      LocalChangesWouldBeOverwrittenHelper.showErrorDialog(
        project,
        operationName,
        null,
        localChanges,
        affectedPaths.map { it.path }
      )
    })

    if (localChanges.isNotEmpty()) {
      addAction(saveAndRetryAction(repository, operationName, retryAction))
    }
  }

  private fun saveAndRetryAction(
    repository: GitRepository,
    operationName: @Nls String,
    retryAction: (GitChangesSaver) -> Unit,
  ): NotificationAction {
    val savingStrategy = getSavingStrategy(repository.project)
    val actionText = GitBundle.message("apply.changes.save.and.retry.operation", savingStrategy.text)
    return NotificationAction.createExpiring(actionText) { _, _ ->
      GitDisposable.getInstance(repository.project).coroutineScope.launch {
        withBackgroundProgress(repository.project, savingStrategy.selectBundleMessage(
          GitBundle.message("stashing.progress.title"),
          VcsBundle.message("shelve.changes.progress.text")
        )) {
          val changesSaver = GitChangesSaver.getSaver(repository.project, Git.getInstance(), EmptyProgressIndicator(),
                                                      VcsBundle.message("stash.changes.message", operationName), savingStrategy)
          retryAction(changesSaver)
        }
      }
    }
  }

  private companion object {
    fun getSavingStrategy(project: Project) =
      if (GitVcsApplicationSettings.getInstance().isStagingAreaEnabled) GitSaveChangesPolicy.STASH
      else GitVcsSettings.getInstance(project).saveChangesPolicy


    fun getDescription(
      operationName: @Nls String,
      repository: GitRepository,
      failedOnCommit: VcsCommitMetadata?,
      successfulCommits: List<VcsCommitMetadata>,
    ): @Nls String {
      var description = if (failedOnCommit != null) {
        GitApplyChangesProcess.commitDetails(failedOnCommit) + UIUtil.BR
      }
      else ""

      description += GitBundle.message("warning.your.local.changes.would.be.overwritten.by", operationName,
                                       StringUtil.toLowerCase(getSavingStrategy(repository.project).text))
      description += GitApplyChangesProcess.getSuccessfulCommitDetailsIfAny(successfulCommits, operationName)
      return description
    }
  }
}
