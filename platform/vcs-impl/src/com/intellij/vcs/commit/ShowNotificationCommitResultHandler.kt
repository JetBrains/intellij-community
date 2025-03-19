// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_CANCELED
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FAILED
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED_WITH_WARNINGS
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.commit.Committer.Companion.collectErrors
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

private fun hasOnlyWarnings(exceptions: List<VcsException>) = exceptions.all { it.isWarning }

class ShowNotificationCommitResultHandler(private val committer: VcsCommitter) : CommitterResultHandler {
  private val notifier = VcsNotifier.getInstance(committer.project)

  override fun onSuccess() = reportResult()
  override fun onCancel() {
    notifier.notifyMinorWarning(COMMIT_CANCELED, "", message("vcs.commit.canceled"))
  }

  override fun onFailure() = reportResult()

  private fun reportResult() {
    val message = getCommitSummary()

    val allExceptions = committer.exceptions

    val notification = if (allExceptions.isEmpty()) {
      CommitNotification(VcsNotifier.standardNotification().displayId, "", message)
    } else {
      val errors = collectErrors(allExceptions)
      val errorsSize = errors.size
      val warningsSize = allExceptions.size - errorsSize
      val notificationActions = allExceptions.filterIsInstance<CommitExceptionWithActions>().flatMap { it.actions }

      val title: @NlsContexts.NotificationTitle String
      val displayId: @NonNls String
      val notificationType: NotificationType
      if (errorsSize > 0) {
        displayId = COMMIT_FAILED
        title = message("message.text.commit.failed.with.error", errorsSize)
        notificationType = NotificationType.ERROR
      }
      else {
        displayId = COMMIT_FINISHED_WITH_WARNINGS
        title = message("message.text.commit.finished.with.warning", warningsSize)
        notificationType = NotificationType.WARNING
      }

      CommitNotification(VcsNotifier.importantNotification().displayId, title, message, notificationType).apply {
        setDisplayId(displayId)
        notificationActions.forEach(this::addAction)
        VcsNotifier.addShowDetailsAction(committer.project, this)
      }
    }
    notification.expirePreviousAndNotify(committer.project)
  }

  @NlsContexts.NotificationContent
  private fun getCommitSummary() = HtmlBuilder().apply {
    append(getFileSummaryReport())
    val commitMessage = committer.commitMessage
    if (!isEmpty(commitMessage)) {
      append(": ").append(commitMessage) // NON-NLS
    }
    val feedback = committer.feedback
    if (feedback.isNotEmpty()) {
      br()
      appendWithSeparators(HtmlChunk.br(), feedback.map(HtmlChunk::text))
    }
    val exceptions = committer.exceptions
    if (!hasOnlyWarnings(exceptions)) {
      br()
      appendWithSeparators(HtmlChunk.br(), exceptions.map { HtmlChunk.text(it.message) })
    }
  }.toString()

  private fun getFileSummaryReport(): @Nls String {
    val failed = countChangesIgnoringChangeLists(committer.failedToCommitChanges)
    val committed = countChangesIgnoringChangeLists(committer.changes) - failed

    if (failed > 0) {
      return message("vcs.commit.files.committed.and.files.failed.to.commit", committed, failed)
    }
    return message("vcs.commit.files.committed", committed)
  }

  private fun countChangesIgnoringChangeLists(changes: Collection<Change>): Int {
    return HashSet(changes).size
  }
}