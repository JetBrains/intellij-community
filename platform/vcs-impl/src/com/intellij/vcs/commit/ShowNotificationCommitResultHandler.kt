// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.vcs.commit.AbstractCommitter.Companion.collectErrors
import org.jetbrains.annotations.Nls

private val FROM = listOf("<", ">") // NON-NLS // NON-NLS
private val TO = listOf("&lt;", "&gt;") // NON-NLS // NON-NLS

private const val BR = "<br/>" // NON-NLS

/*
  Commit message is passed to NotificationManagerImpl#doNotify and displayed as HTML.
  Thus HTML tag braces (< and >) should be escaped,
  but only they since the text is passed directly to HTML <BODY> tag and is not a part of an attribute or else.
 */
private fun escape(s: String) = replace(s, FROM, TO)

private fun hasOnlyWarnings(exceptions: List<VcsException>) = exceptions.all { it.isWarning }

class ShowNotificationCommitResultHandler(private val committer: AbstractCommitter) : CommitResultHandler {
  private val notifier = VcsNotifier.getInstance(committer.project)

  override fun onSuccess(commitMessage: String) = reportResult()
  override fun onCancel() {
    notifier.notifyMinorWarning("", message("vcs.commit.canceled"))
  }
  override fun onFailure(errors: List<VcsException>) = reportResult()

  private fun reportResult() {
    val allExceptions = committer.exceptions
    val errors = collectErrors(allExceptions)
    val errorsSize = errors.size
    val warningsSize = allExceptions.size - errorsSize
    val message = getCommitSummary()

    when {
      errorsSize > 0 -> {
        val title = message("message.text.commit.failed.with.error", errorsSize)
        notifier.notifyError(title, message)
      }
      warningsSize > 0 -> {
        val title = message("message.text.commit.finished.with.warning", warningsSize)
        notifier.notifyImportantWarning(title, message)
      }
      else -> notifier.notifySuccess(message)
    }
  }

  private fun getCommitSummary() = StringBuilder(getFileSummaryReport()).apply {
    val commitMessage = committer.commitMessage
    if (!isEmpty(commitMessage)) {
      append(": ").append(escape(commitMessage)) // NON-NLS
    }
    val feedback = committer.feedback
    if (feedback.isNotEmpty()) {
      append(BR)
      append(join(feedback, BR))
    }
    val exceptions = committer.exceptions
    if (!hasOnlyWarnings(exceptions)) {
      append(BR)
      append(join(exceptions, { it.message }, BR))
    }
  }.toString()

  private fun getFileSummaryReport(): @Nls String {
    val failed = committer.failedToCommitChanges.size
    val committed = committer.changes.size - failed

    if (failed > 0) {
      return message("vcs.commit.files.committed.and.files.failed.to.commit", committed, failed)
    }
    return message("vcs.commit.files.committed", committed)
  }
}