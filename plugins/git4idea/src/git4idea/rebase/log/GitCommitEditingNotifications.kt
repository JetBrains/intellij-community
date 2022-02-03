// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitNotificationIdsHolder
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

internal fun GitCommitEditingOperationResult.Complete.notifySuccess(
  @NlsContexts.NotificationTitle title: String,
  @NlsContexts.ProgressTitle undoProgressTitle: String,
  @NlsContexts.ProgressTitle undoImpossibleTitle: String,
  @NlsContexts.ProgressTitle undoErrorTitle: String
) {
  val project = repository.project
  val notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(title, NotificationType.INFORMATION)
  notification.setDisplayId(GitNotificationIdsHolder.COMMIT_EDIT_SUCCESS)
  notification.addAction(NotificationAction.createSimple(
    GitBundle.messagePointer("action.NotificationAction.GitRewordOperation.text.undo"),
    Runnable {
      notification.expire()
      undoInBackground(project, undoProgressTitle, undoImpossibleTitle, undoErrorTitle, this@notifySuccess)
    }
  ))

  val connection = project.messageBus.connect()
  notification.whenExpired { connection.disconnect() }
  connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
    if (it == repository) {
      BackgroundTaskUtil.executeOnPooledThread(repository, Runnable {
        if (checkUndoPossibility() !== UndoPossibility.Possible) {
          notification.expire()
        }
      })
    }
  })

  VcsNotifier.getInstance(project).notify(notification)
}

internal fun UndoResult.Error.notifyUndoError(project: Project, @NlsContexts.NotificationTitle title: String) {
  VcsNotifier.getInstance(project).notifyError(REBASE_COMMIT_EDIT_UNDO_ERROR, title, errorHtml)
}

internal fun UndoPossibility.Impossible.notifyUndoImpossible(project: Project, @NlsContexts.NotificationTitle title: String) {
  val notifier = VcsNotifier.getInstance(project)
  when (this) {
    UndoPossibility.Impossible.HeadMoved -> {
      notifier.notifyError(REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES,
                           title,
                           GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.repository.changed.message"))
    }
    is UndoPossibility.Impossible.PushedToProtectedBranch -> {
      notifier.notifyError(REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH,
                           title,
                           GitBundle.message("rebase.log.undo.impossible.pushed.to.protected.branch.notification.text", branch))
    }
  }
}

private fun undoInBackground(
  project: Project,
  @NlsContexts.ProgressTitle undoProgressTitle: String,
  @NlsContexts.ProgressTitle undoImpossibleTitle: String,
  @NlsContexts.ProgressTitle undoErrorTitle: String,
  result: GitCommitEditingOperationResult.Complete
) {
  ProgressManager.getInstance().run(object : Task.Backgroundable(project, undoProgressTitle) {
    override fun run(indicator: ProgressIndicator) {
      val possibility = result.checkUndoPossibility()
      if (possibility is UndoPossibility.Impossible) {
        possibility.notifyUndoImpossible(project, undoImpossibleTitle)
        return
      }
      val undoResult = result.undo()
      if (undoResult is UndoResult.Error) {
        undoResult.notifyUndoError(project, undoErrorTitle)
      }
    }
  })
}
