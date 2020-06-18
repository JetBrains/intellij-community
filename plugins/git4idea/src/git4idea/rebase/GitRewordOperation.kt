// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitConfigUtil
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry.Action.PICK
import git4idea.rebase.GitRebaseEntry.Action.REWORD
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoResult
import git4idea.rebase.log.GitMultipleCommitEditingOperation
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.io.File
import java.io.IOException

internal class GitRewordOperation(
  repository: GitRepository,
  private val commit: VcsCommitMetadata,
  private val newMessage: String
) : GitMultipleCommitEditingOperation(repository) {
  init {
    repository.update()
  }

  private val LOG = logger<GitRewordOperation>()
  private val notifier = VcsNotifier.getInstance(project)

  private val initialHeadPosition = repository.currentRevision!!

  fun execute(): GitMultipleCommitEditingOperationResult {
    var operationResult: GitMultipleCommitEditingOperationResult = GitMultipleCommitEditingOperationResult.Incomplete
    if (canRewordViaAmend()) {
      operationResult = rewordViaAmend()
    }
    if (operationResult is GitMultipleCommitEditingOperationResult.Incomplete) {
      operationResult = rewordViaRebase()
    }

    if (operationResult is GitMultipleCommitEditingOperationResult.Complete) {
      notifySuccess(operationResult)
      ChangeListManagerImpl.getInstanceImpl(project).replaceCommitMessage(commit.fullMessage, newMessage)
    }
    return operationResult
  }

  private fun canRewordViaAmend() =
    isLatestCommit() && GitVersionSpecialty.CAN_AMEND_WITHOUT_FILES.existsIn(project)

  private fun isLatestCommit() = commit.id.asString() == initialHeadPosition

  private fun rewordViaRebase(): GitMultipleCommitEditingOperationResult {
    val rebaseEditor = GitAutomaticRebaseEditor(project, commit.root,
                                                entriesEditor = { list -> injectRewordAction(list) },
                                                plainTextEditor = { editorText -> supplyNewMessage(editorText) })

    return rebase(listOf(commit), rebaseEditor)
  }

  private fun rewordViaAmend(): GitMultipleCommitEditingOperationResult {
    val handler = GitLineHandler(project, repository.root, GitCommand.COMMIT)
    val messageFile: File
    try {
      messageFile = GitCheckinEnvironment.createCommitMessageFile(project, repository.root, newMessage)
    }
    catch (e: IOException) {
      LOG.warn("Couldn't create message file", e)
      return GitMultipleCommitEditingOperationResult.Incomplete
    }
    handler.addParameters("--amend")
    handler.addParameters("-F")
    handler.addAbsoluteFile(messageFile)
    handler.addParameters("--only") // without any files: to amend only the message
    handler.addParameters("--no-verify") // to prevent unnecessary hooks execution

    val result = Git.getInstance().runCommand(handler)
    repository.update()
    if (result.success()) {
      return GitMultipleCommitEditingOperationResult.Complete(repository, commit.parents.first().asString(), initialHeadPosition,
                                                              repository.currentRevision!!)
    }
    else {
      LOG.warn("Couldn't reword via amend: " + result.errorOutputAsJoinedString)
      return GitMultipleCommitEditingOperationResult.Incomplete
    }
  }

  internal fun undo(operationResult: GitMultipleCommitEditingOperationResult.Complete) {
    val possibility = operationResult.checkUndoPossibility()
    val errorTitle = GitBundle.getString("rebase.log.reword.action.notification.undo.not.allowed.title")
    when (possibility) {
      is UndoPossibility.HeadMoved -> notifier.notifyError(
        errorTitle,
        GitBundle.getString("rebase.log.reword.action.notification.undo.not.allowed.repository.changed.message")
      )
      is UndoPossibility.PushedToProtectedBranch -> notifier.notifyError(
        errorTitle,
        GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.commit.pushed.message", possibility.branch)
      )
      UndoPossibility.Possible -> doUndo(operationResult)
    }
  }

  private fun doUndo(operationResult: GitMultipleCommitEditingOperationResult.Complete) {
    val undoResult = operationResult.undo()
    if (undoResult is UndoResult.Error) {
      notifier.notifyError(GitBundle.getString("rebase.log.reword.action.notification.undo.failed.title"), undoResult.errorHtml)
    }
  }

  private fun injectRewordAction(list: List<GitRebaseEntry>): List<GitRebaseEntry> {
    return list.map { entry ->
      if (entry.action == PICK && commit.id.asString().startsWith(entry.commit))
        GitRebaseEntry(REWORD, entry.commit, entry.subject)
      else entry
    }
  }

  private fun supplyNewMessage(editorText: String): String {
    if (editorText.startsWith(commit.fullMessage)) { // there are comments after the proposed message
      return newMessage
    }
    else {
      LOG.error("Unexpected editor content. Charset: ${GitConfigUtil.getCommitEncoding(project, commit.root)}",
                Attachment("actual.txt", editorText), Attachment("expected.txt", commit.fullMessage))
      throw IllegalStateException("Unexpected editor content")
    }
  }

  private fun notifySuccess(operationResult: GitMultipleCommitEditingOperationResult.Complete) {
    val notification = STANDARD_NOTIFICATION.createNotification(
      GitBundle.getString("rebase.log.reword.action.notification.successful.title"),
      "",
      NotificationType.INFORMATION,
      null
    )
    notification.addAction(NotificationAction.createSimple(
      GitBundle.messagePointer("action.NotificationAction.GitRewordOperation.text.undo"),
      Runnable {
        notification.expire()
        undoInBackground(operationResult)
      }))

    val connection = project.messageBus.connect()
    notification.whenExpired { connection.disconnect() }
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      if (it == repository) {
        BackgroundTaskUtil.executeOnPooledThread(repository, Runnable {
          if (operationResult.checkUndoPossibility() !== UndoPossibility.Possible) {
            notification.expire()
          }
        })
      }
    })

    notifier.notify(notification)
  }

  private fun undoInBackground(operationResult: GitMultipleCommitEditingOperationResult.Complete) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(
      project,
      GitBundle.getString("rebase.log.reword.action.progress.indicator.undo.title")
    ) {
      override fun run(indicator: ProgressIndicator) {
        undo(operationResult)
      }
    })
  }
}