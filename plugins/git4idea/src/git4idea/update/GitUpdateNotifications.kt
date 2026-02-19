// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.notification.NotificationAction
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

internal fun VcsNotifier.notifyDetachedHeadError(repository: GitRepository) {
  notifyError(GitNotificationIdsHolder.UPDATE_DETACHED_HEAD_ERROR, GitBundle.message("notification.title.can.t.update.no.current.branch"),
              GitUpdateProcess.getDetachedHeadErrorNotificationContent(repository))
}

internal fun VcsNotifier.notifyNoTrackedBranchError(repository: GitRepository, currentBranch: GitLocalBranch) {
  notifyError(
    GitNotificationIdsHolder.UPDATE_NO_TRACKED_BRANCH, GitBundle.message("update.notification.update.error"),
    GitUpdateProcess.getNoTrackedBranchError(repository, currentBranch.name),
    NotificationAction.createSimple(
      GitBundle.message("update.notification.choose.upstream.branch"),
      Runnable {
        showUpdateDialog(repository)
      })
  )
}

private fun showUpdateDialog(repository: GitRepository) {
  val updateDialog = FixTrackedBranchDialog(repository.getProject())

  if (updateDialog.showAndGet()) {
    GitUpdateExecutionProcess.launchUpdate(repository.getProject(),
                                           listOf(repository),
                                           updateDialog.updateConfig,
                                           updateDialog.updateMethod,
                                           updateDialog.shouldSetAsTrackedBranch())
  }
}