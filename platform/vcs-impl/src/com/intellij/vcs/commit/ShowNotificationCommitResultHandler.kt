// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_CANCELED
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FAILED
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED_INITIAL
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED_WITH_WARNINGS
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ShareProjectActionProvider
import com.intellij.vcs.commit.Committer.Companion.collectErrors
import org.jetbrains.annotations.NonNls

private fun hasOnlyWarnings(exceptions: List<VcsException>) = exceptions.all { it.isWarning }

class ShowNotificationCommitResultHandler(private val committer: VcsCommitter) : CommitterResultHandler {
  private val notifier = VcsNotifier.getInstance(committer.project)

  override fun onSuccess(): Unit = reportResult()
  override fun onCancel() {
    notifier.notifyMinorWarning(COMMIT_CANCELED, "", message("vcs.commit.canceled"))
  }

  override fun onFailure(): Unit = reportResult()

  private fun reportResult() {
    val message = getCommitSummary()

    val commitExceptions = committer.exceptions
    val commitErrors = collectErrors(commitExceptions)
    val warningsSize = commitExceptions.size - commitErrors.size

    val notificationActions = commitExceptions.filterIsInstance<CommitExceptionWithActions>().flatMap { it.actions }

    val changesFailedToCommit = countChangesIgnoringChangeLists(committer.failedToCommitChanges)
    val changesCommitted = countChangesIgnoringChangeLists(committer.changes) - changesFailedToCommit

    val freshRoot = committer.commitContext.freshRoots?.singleOrNull()

    val type =
      if (commitErrors.isNotEmpty()) CommitNotificationType.Failed
      else if (commitExceptions.isNotEmpty()) CommitNotificationType.SuccessfulWithWarnings
      else if (freshRoot != null) CommitNotificationType.SuccessfulInitial
      else CommitNotificationType.Successful

    val displayId: @NonNls String = type.displayId
    val notificationType: NotificationType = type.notificationType

    val title: @NlsContexts.NotificationTitle String = when (type) {
      CommitNotificationType.Failed -> message("message.text.commit.failed.with.error", commitErrors.size)
      CommitNotificationType.SuccessfulWithWarnings -> message("message.text.commit.finished.with.warning", warningsSize)
      else -> message("vcs.commit.files.committed", changesCommitted)
    }

    val notification = CommitNotification(VcsNotifier.importantNotification().displayId, title, message, notificationType).apply {
      setDisplayId(displayId)

      if (commitExceptions.isNotEmpty()) {
        notificationActions.forEach(this::addAction)
        VcsNotifier.addShowDetailsAction(committer.project, this)
      }

      if (freshRoot != null && commitErrors.isEmpty()) {
        ShareProjectActionProvider.EP_NAME.extensionList
          .filter { it.isApplicableForRoot(committer.project, freshRoot) }
          .forEachIndexed { index, ep ->
            addAction(NotificationAction.create(
              if (index == 0) message("vcs.commit.notification.shareProjectOn", ep.hostServiceName) else message("vcs.commit.notification.shareProjectOn.orOn", ep.hostServiceName),
              { e, _ ->
                ep.action.actionPerformed(e.withDataContext(
                  SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE, freshRoot, e.dataContext)
                ))
              }
            ))
          }
      }
    }

    notification.expirePreviousAndNotify(committer.project)
  }

  @NlsContexts.NotificationContent
  private fun getCommitSummary() = HtmlBuilder().apply {
    val commitMessage = committer.commitMessage
    if (!isEmpty(commitMessage)) {
      append(commitMessage) // NON-NLS
    }
    val feedback = committer.feedback
    if (feedback.isNotEmpty()) {
      if (!this@apply.isEmpty) br()
      appendWithSeparators(HtmlChunk.br(), feedback.map(HtmlChunk::text))
    }
    val exceptions = committer.exceptions
    if (!hasOnlyWarnings(exceptions)) {
      if (!this@apply.isEmpty) br()
      appendWithSeparators(HtmlChunk.br(), exceptions.map { HtmlChunk.text(it.message) })
    }
  }.toString()

  private enum class CommitNotificationType(
    val displayId: String,
    val notificationType: NotificationType
  ) {
    Successful(COMMIT_FINISHED, NotificationType.INFORMATION),
    SuccessfulInitial(COMMIT_FINISHED_INITIAL, NotificationType.INFORMATION),
    SuccessfulWithWarnings(COMMIT_FINISHED_WITH_WARNINGS, NotificationType.WARNING),
    Failed(COMMIT_FAILED, NotificationType.ERROR);
  }

  private fun countChangesIgnoringChangeLists(changes: Collection<Change>): Int {
    return HashSet(changes).size
  }
}