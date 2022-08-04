// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.Nls

class CustomCommitter(
  private val project: Project,
  private val session: CommitSession,
  private val changes: List<Change>,
  private val commitMessage: String
) {
  private val resultHandlers = mutableListOf<CommitResultHandler>()

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  fun runCommit(taskName: @Nls String) = object : Task.Modal(project, removeMnemonic(taskName), true) {
    override fun run(indicator: ProgressIndicator) = session.execute(changes, commitMessage)

    override fun onSuccess() {
      LOG.debug("Commit successful")
      resultHandlers.forEachLoggingErrors(LOG) { it.onSuccess(commitMessage) }
    }

    override fun onCancel() {
      LOG.debug("Commit canceled")
      session.executionCanceled()
      resultHandlers.forEachLoggingErrors(LOG) { it.onCancel() }
    }

    override fun onThrowable(error: Throwable) {
      showErrorDialog(message("error.executing.commit", taskName, error.localizedMessage), taskName)

      val errors = listOf(VcsException(error))
      resultHandlers.forEachLoggingErrors(LOG) { it.onFailure(errors) }
    }
  }.queue()

  companion object {
    private val LOG = logger<CustomCommitter>()
  }
}