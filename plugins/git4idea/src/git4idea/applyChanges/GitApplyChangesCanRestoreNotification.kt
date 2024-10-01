// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitApplyChangesNotification
import git4idea.GitDisposable
import git4idea.GitRestoreSavedChangesNotificationAction
import git4idea.i18n.GitBundle
import git4idea.stash.GitChangesSaver
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

internal class GitApplyChangesCanRestoreNotification(
  project: Project,
  changesSaver: GitChangesSaver,
  operationName: @Nls String,
) : GitApplyChangesNotification(
  VcsNotifier.importantNotification().displayId,
  GitBundle.message("apply.changes.restore.notification.title"),
  GitBundle.message("apply.changes.restore.notification.description", operationName),
  NotificationType.INFORMATION,
) {
  init {
    addAction(GitRestoreSavedChangesNotificationAction(changesSaver))
    addAction(NotificationAction.createExpiring(changesSaver.saveMethod.selectBundleMessage(
      GitBundle.message("unstash.title"),
      VcsBundle.message("unshelve.changes.action")
    )) { _, _ ->
      GitDisposable.getInstance(project).coroutineScope.launch {
        withBackgroundProgress(project, changesSaver.saveMethod.selectBundleMessage(
          GitBundle.message("unstash.unstashing"),
          VcsBundle.message("unshelve.changes.progress.title")
        )) {
          changesSaver.load()
        }
      }
    })
  }
}