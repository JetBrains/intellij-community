// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    val changesCommitted =
      committer.changes.countChangesIgnoringChangeLists() - committer.failedToCommitChanges.countChangesIgnoringChangeLists()

    val freshRoot = committer.commitContext.freshUnhostedRoots?.singleOrNull()

    val type = when {
      commitErrors.isNotEmpty() -> CommitNotificationType.Failed
      commitExceptions.isNotEmpty() -> CommitNotificationType.SuccessfulWithWarnings
      freshRoot != null -> CommitNotificationType.SuccessfulInitial
      else -> CommitNotificationType.Successful
    }

    val title: @NlsContexts.NotificationTitle String = when (type) {
      CommitNotificationType.Failed -> message("message.text.commit.failed.with.error", commitErrors.size)
      CommitNotificationType.SuccessfulWithWarnings -> message("message.text.commit.finished.with.warning", warningsSize)
      CommitNotificationType.Successful, CommitNotificationType.SuccessfulInitial -> message("vcs.commit.files.committed", changesCommitted)
    }

    val notification = CommitNotification(VcsNotifier.importantNotification().displayId, title, message, type.notificationType).apply {
      setDisplayId(type.displayId)

      if (commitExceptions.isNotEmpty()) {
        val exceptionActions = commitExceptions.filterIsInstance<CommitExceptionWithActions>()
          .flatMap { it.getActions(this) }
        exceptionActions.forEach(this::addAction)

        val shouldAddShowDetailsAction = commitExceptions.any { exception ->
          exception !is CommitExceptionWithActions || exception.shouldAddShowDetailsAction
        }
        if (shouldAddShowDetailsAction) {
          VcsNotifier.addShowDetailsAction(committer.project, this)
        }
      }

      if (commitErrors.isEmpty()) {
        addActions(CommitSuccessNotificationActionProvider.EP_NAME.extensionList.flatMap { it.getActions(committer, this) })
      }

      if (freshRoot != null && commitErrors.isEmpty()) {
        ShareProjectActionProvider.EP_NAME.extensionList
          .filter { it.isApplicableForRoot(committer.project, freshRoot) }
          .forEachIndexed { index, ep ->
            addAction(NotificationAction.create(
              if (index == 0) message("vcs.commit.notification.shareProjectOn",
                                      ep.hostServiceName)
              else message("vcs.commit.notification.shareProjectOn.orOn", ep.hostServiceName)
            ) { e, _ ->
              ep.action.actionPerformed(e.withDataContext(
                SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE, freshRoot, e.dataContext)
              ))
            })
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
    val notificationType: NotificationType,
  ) {
    Successful(COMMIT_FINISHED, NotificationType.INFORMATION),
    SuccessfulInitial(COMMIT_FINISHED_INITIAL, NotificationType.INFORMATION),
    SuccessfulWithWarnings(COMMIT_FINISHED_WITH_WARNINGS, NotificationType.WARNING),
    Failed(COMMIT_FAILED, NotificationType.ERROR);
  }

  private fun Collection<Change>.countChangesIgnoringChangeLists() = HashSet(this).size
}

private fun hasOnlyWarnings(exceptions: List<VcsException>) = exceptions.all { it.isWarning }