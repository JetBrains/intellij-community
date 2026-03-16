// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.ui.VcsLogUiEx
import git4idea.GitDisposable
import git4idea.GitNotificationIdsHolder
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH
import git4idea.GitNotificationIdsHolder.Companion.REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.launch

internal fun List<GitCommitEditingOperationResult.Complete>.notifySuccess(
  @NlsContexts.NotificationTitle title: String,
  @NlsContexts.NotificationContent content: String?,
  @NlsContexts.ProgressTitle undoProgressTitle: String,
  @NlsContexts.ProgressTitle undoImpossibleTitle: String,
  @NlsContexts.ProgressTitle undoErrorTitle: String,
  logUiEx: VcsLogUiEx? = null,
  editAgain: (() -> Unit)? = null,
) {

  val resultsByRepository = associateBy { it.repository }
  val project = first().repository.project
  val notification = if (content.isNullOrEmpty()) {
    VcsNotifier.standardNotification().createNotification(title, NotificationType.INFORMATION)
  }
  else {
    VcsNotifier.standardNotification().createNotification(title, content, NotificationType.INFORMATION)
  }
  notification.setDisplayId(GitNotificationIdsHolder.COMMIT_EDIT_SUCCESS)
  notification.addAction(NotificationAction.createSimple(
    GitBundle.messagePointer("action.NotificationAction.GitRewordOperation.text.undo"),
    Runnable {
      forEach { completeResult ->
        undoInBackground(project, undoProgressTitle, undoImpossibleTitle, undoErrorTitle, completeResult, logUiEx) { notification.expire() }
      }
    }
  ))

  editAgain?.let {
    notification.addAction(NotificationAction.createSimpleExpiring(
      GitBundle.message("action.NotificationAction.GitRewordOperation.text.edit.again"),
      Runnable { it() }
    ))
  }

  val connection = project.messageBus.connect()
  notification.whenExpired { connection.disconnect() }
  connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
    BackgroundTaskUtil.executeOnPooledThread(it, Runnable {
      resultsByRepository[it]?.let { completeResult ->
        if (completeResult.checkUndoPossibility() !== UndoPossibility.Possible) {
          notification.expire()
        }
      }
    })
  })

  VcsNotifier.getInstance(project).notify(notification)
}

internal fun GitCommitEditingOperationResult.Complete.notifySuccess(
  @NlsContexts.NotificationTitle title: String,
  @NlsContexts.NotificationContent content: String?,
  @NlsContexts.ProgressTitle undoProgressTitle: String,
  @NlsContexts.ProgressTitle undoImpossibleTitle: String,
  @NlsContexts.ProgressTitle undoErrorTitle: String,
  logUiEx: VcsLogUiEx? = null,
  editAgain: (() -> Unit)? = null,
) = listOf(this).notifySuccess(title, content, undoProgressTitle, undoImpossibleTitle, undoErrorTitle, logUiEx, editAgain)

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
  result: GitCommitEditingOperationResult.Complete,
  logUiEx: VcsLogUiEx?,
  expireUndoAction: () -> Unit,
) {
  GitDisposable.getInstance(project).coroutineScope.launch {
    withBackgroundProgress(project, undoProgressTitle) {
      val possibility = result.checkUndoPossibility()
      if (possibility is UndoPossibility.Impossible) {
        possibility.notifyUndoImpossible(project, undoImpossibleTitle)
        expireUndoAction()
        return@withBackgroundProgress
      }
      when (val undoResult = result.undo()) {
        is UndoResult.Error -> undoResult.notifyUndoError(project, undoErrorTitle)
        is UndoResult.Success -> {
          logUiEx?.focusCommitWhenReady(result.repository, result.commitToFocusOnUndo)
          expireUndoAction()
        }
      }
    }
  }
}
