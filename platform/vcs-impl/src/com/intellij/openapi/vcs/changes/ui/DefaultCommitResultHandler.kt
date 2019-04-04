// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.ui.AbstractCommitter.Companion.collectErrors

private val FROM = listOf("<", ">")
private val TO = listOf("&lt;", "&gt;")

/*
  Commit message is passed to NotificationManagerImpl#doNotify and displayed as HTML.
  Thus HTML tag braces (< and >) should be escaped,
  but only they since the text is passed directly to HTML <BODY> tag and is not a part of an attribute or else.
 */
private fun escape(s: String) = replace(s, FROM, TO)

private fun hasOnlyWarnings(exceptions: List<VcsException>) = exceptions.all { it.isWarning }

class DefaultCommitResultHandler(private val committer: AbstractCommitter) : CommitResultHandler {

  override fun onSuccess(commitMessage: String) = reportResult()
  override fun onFailure() = reportResult()

  private fun reportResult() {
    val allExceptions = committer.exceptions
    val errors = collectErrors(allExceptions)
    val errorsSize = errors.size
    val warningsSize = allExceptions.size - errorsSize

    val notifier = VcsNotifier.getInstance(committer.project)
    val message = getCommitSummary()

    when {
      errorsSize > 0 -> {
        val title = pluralize(message("message.text.commit.failed.with.error"), errorsSize)
        notifier.notifyError(title, message)
      }
      warningsSize > 0 -> {
        val title = pluralize(message("message.text.commit.finished.with.warning"), warningsSize)
        notifier.notifyImportantWarning(title, message)
      }
      else -> notifier.notifySuccess(message)
    }
  }

  private fun getCommitSummary() = StringBuilder(getFileSummaryReport()).apply {
    val commitMessage = committer.commitMessage
    if (!isEmpty(commitMessage)) {
      append(": ").append(escape(commitMessage))
    }
    val feedback = committer.feedback
    if (!feedback.isEmpty()) {
      append("<br/>")
      append(join(feedback, "<br/>"))
    }
    val exceptions = committer.exceptions
    if (!hasOnlyWarnings(exceptions)) {
      append("<br/>")
      append(join(exceptions, { it.message }, "<br/>"))
    }
  }.toString()

  private fun getFileSummaryReport(): String {
    val failed = committer.failedToCommitChanges.size
    val committed = committer.changes.size - failed

    var fileSummary = "$committed ${pluralize("file", committed)} committed"
    if (failed > 0) {
      fileSummary += ", $failed ${pluralize("file", failed)} failed to commit"
    }
    return fileSummary
  }
}