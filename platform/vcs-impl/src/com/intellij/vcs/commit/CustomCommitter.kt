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
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.Nls

private val LOG = logger<CustomCommitter>()

class CustomCommitter(
  private val project: Project,
  private val session: CommitSession,
  private val changes: List<Change>,
  private val commitMessage: String
) {

  private val resultHandlers = createLockFreeCopyOnWriteList<CommitResultHandler>()

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  fun runCommit(taskName: @Nls String) = object : Task.Modal(project, removeMnemonic(taskName), true) {
    override fun run(indicator: ProgressIndicator) = session.execute(changes, commitMessage)

    override fun onSuccess() {
      LOG.debug("Commit successful")
      resultHandlers.forEach { it.onSuccess(commitMessage) }
    }

    override fun onCancel() {
      LOG.debug("Commit canceled")
      session.executionCanceled()
      resultHandlers.forEach { it.onCancel() }
    }

    override fun onThrowable(error: Throwable) {
      showErrorDialog(message("error.executing.commit", taskName, error.localizedMessage), taskName)

      val errors = listOf(VcsException(error))
      resultHandlers.forEach { it.onFailure(errors) }
    }
  }.queue()
}