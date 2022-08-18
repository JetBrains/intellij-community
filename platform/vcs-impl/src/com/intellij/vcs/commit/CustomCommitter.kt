// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.Nls

class CustomCommitter(
  project: Project,
  private val session: CommitSession,
  private val changes: List<Change>,
  commitMessage: String
) : Committer(project, commitMessage) {
  fun runCommit(taskName: @Nls String) {
    addResultHandler(CustomCommitResultHandler(session, taskName))

    runModalTask(removeMnemonic(taskName), project, true) {
      runCommitTask {
        session.execute(changes, commitMessage)
      }
    }
  }

  private class CustomCommitResultHandler(val session: CommitSession, val taskName: @Nls String) : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = Unit

    override fun onCancel() {
      session.executionCanceled()
    }

    override fun onFailure(errors: List<VcsException>) {
      val error = errors.firstOrNull() ?: return
      runInEdt {
        showErrorDialog(message("error.executing.commit", taskName, error.localizedMessage), taskName)
      }
    }
  }
}