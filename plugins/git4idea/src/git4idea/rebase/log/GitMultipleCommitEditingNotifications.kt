// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoResult
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

internal fun GitMultipleCommitEditingOperationResult.Complete.notifySuccess(
  title: @NlsContexts.NotificationTitle String,
  undoProgressTitle: @NlsContexts.ProgressTitle String,
  undoImpossibleTitle: @NlsContexts.ProgressTitle String,
  undoErrorTitle: @NlsContexts.ProgressTitle String
) {
  val project = repository.project
  val notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(title, "", NotificationType.INFORMATION, null)
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

internal fun UndoResult.Error.notifyUndoError(
  project: Project,
  title: @NlsContexts.NotificationTitle String
) {
  VcsNotifier.getInstance(project).notifyError(title, errorHtml)
}

internal fun UndoPossibility.Impossible.notifyUndoImpossible(project: Project, title: @NlsContexts.NotificationTitle String) {
  val notifier = VcsNotifier.getInstance(project)
  when (this) {
    UndoPossibility.Impossible.HeadMoved -> {
      notifier.notifyError(title, GitBundle.getString("rebase.log.reword.action.notification.undo.not.allowed.repository.changed.message"))
    }
    is UndoPossibility.Impossible.PushedToProtectedBranch -> {
      notifier.notifyError(title, GitBundle.message("rebase.log.undo.impossible.pushed.to.protected.branch.notification.text", branch))
    }
  }
}

private fun undoInBackground(
  project: Project,
  undoProgressTitle: @NlsContexts.ProgressTitle String,
  undoImpossibleTitle: @NlsContexts.ProgressTitle String,
  undoErrorTitle: @NlsContexts.ProgressTitle String,
  result: GitMultipleCommitEditingOperationResult.Complete
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