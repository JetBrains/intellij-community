// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.ui.CommitHelper.collectErrors
import java.util.*

class DefaultCommitResultHandler(private val myHelper: CommitHelper) : CommitResultHandler {
  override fun onSuccess(commitMessage: String) {
    reportResult()
  }

  override fun onFailure() {
    reportResult()
  }

  private fun reportResult() {
    val allExceptions = myHelper.exceptions
    val errors = collectErrors(allExceptions)
    val errorsSize = errors.size
    val warningsSize = allExceptions.size - errorsSize

    val notifier = VcsNotifier.getInstance(myHelper.project)
    val message = getCommitSummary()
    if (errorsSize > 0) {
      val title = pluralize(message("message.text.commit.failed.with.error"), errorsSize)
      notifier.notifyError(title, message)
    }
    else if (warningsSize > 0) {
      val title = pluralize(message("message.text.commit.finished.with.warning"), warningsSize)
      notifier.notifyImportantWarning(title, message)
    }
    else {
      notifier.notifySuccess(message)
    }
  }

  private fun getCommitSummary(): String {
    val content = StringBuilder(getFileSummaryReport())
    val commitMessage = myHelper.commitMessage
    if (!isEmpty(commitMessage)) {
      content.append(": ").append(escape(commitMessage))
    }
    val feedback = myHelper.feedback
    if (!feedback.isEmpty()) {
      content.append("<br/>")
      content.append(join(feedback, "<br/>"))
    }
    val exceptions = myHelper.exceptions
    if (!hasOnlyWarnings(exceptions)) {
      content.append("<br/>")
      content.append(join(exceptions, { it.message }, "<br/>"))
    }
    return content.toString()
  }

  private fun getFileSummaryReport(): String {
    val failed = myHelper.failedToCommitChanges.size
    val committed = myHelper.changes.size - failed
    var fileSummary = committed.toString() + " " + pluralize("file", committed) + " committed"
    if (failed > 0) {
      fileSummary += ", " + failed + " " + pluralize("file", failed) + " failed to commit"
    }
    return fileSummary
  }

  /*
    Commit message is passed to NotificationManagerImpl#doNotify and displayed as HTML.
    Thus HTML tag braces (< and >) should be escaped,
    but only they since the text is passed directly to HTML <BODY> tag and is not a part of an attribute or else.
   */
  private fun escape(s: String): String {
    val FROM = Arrays.asList("<", ">")
    val TO = Arrays.asList("&lt;", "&gt;")
    return replace(s, FROM, TO)
  }

  private fun hasOnlyWarnings(exceptions: List<VcsException>): Boolean {
    return exceptions.stream().allMatch { it.isWarning }
  }
}
