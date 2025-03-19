// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil
import git4idea.GitApplyChangesProcess.ConflictResolver
import git4idea.actions.GitAbortOperationAction
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

abstract class GitApplyChangesNotification(
  groupId: String,
  title: @NotificationTitle String,
  content: @NotificationContent String,
  type: NotificationType,
) : Notification(groupId, title, content, type) {
  companion object {
    inline fun <reified T: ExpireAfter> expireAll(project: Project) {
      NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(GitApplyChangesNotification::class.java, project)
        .forEach {
          if (it is T) it.expire()
        }
    }
  }

  sealed interface ExpireAfter
  interface ExpireAfterAbort: ExpireAfter
  interface ExpireAfterRepoStateChanged: ExpireAfter
}

internal class GitApplyChangesConflictNotification(
  operationName: @Nls String,
  description: @NotificationContent String,
  commit: VcsCommitMetadata,
  repository: GitRepository,
  abortCommand: GitAbortOperationAction,
) : GitApplyChangesNotification(
  VcsNotifier.importantNotification().displayId,
  GitBundle.message("apply.changes.operation.performed.with.conflicts", operationName.capitalize()),
  description,
  NotificationType.WARNING,
), GitApplyChangesNotification.ExpireAfterAbort, GitApplyChangesNotification.ExpireAfterRepoStateChanged {
  init {
    setDisplayId(GitNotificationIdsHolder.APPLY_CHANGES_CONFLICTS)

    addAction(NotificationAction.createSimple(GitBundle.message("apply.changes.unresolved.conflicts.notification.resolve.action.text")) {
      val hash = commit.id.toShortString()
      val commitAuthor = VcsUserUtil.getShortPresentation(commit.author)
      val commitMessage = commit.subject
      ConflictResolver(repository.project, repository.root, hash, commitAuthor, commitMessage, operationName).mergeNoProceedInBackground()
    })

    addAction(NotificationAction.create(GitBundle.message("apply.changes.unresolved.conflicts.notification.abort.action.text",
                                                          operationName.capitalize())) {
      abortCommand.performInBackground(repository)
    })
  }
}

internal class GitApplyChangesNothingToDoNotification(
  operationName: @Nls String,
  description: @NotificationContent String
): GitApplyChangesNotification(
  VcsNotifier.importantNotification().displayId,
  GitBundle.message("apply.changes.nothing.to.do", operationName),
  description,
  NotificationType.WARNING,
), GitApplyChangesNotification.ExpireAfterAbort {
  init {
    setDisplayId(GitNotificationIdsHolder.APPLY_CHANGES_SUCCESS)
  }
}
