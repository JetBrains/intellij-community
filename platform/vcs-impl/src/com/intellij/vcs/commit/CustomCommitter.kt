// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class CustomCommitter(
  project: Project,
  private val session: CommitSession,
  private val changes: List<Change>,
  commitMessage: String
) : Committer(project, commitMessage) {
  fun runCommit(taskName: @Nls String) {
    addResultHandler(CustomCommitResultHandler(this, taskName))

    runModalTask(removeMnemonic(taskName), project, true) {
      runCommitTask(false) {
        session.execute(changes, commitMessage)
      }
    }
  }

  private class CustomCommitResultHandler(val committer: CustomCommitter, val taskName: @Nls String) : CommitterResultHandler {
    override fun onCancel() {
      committer.session.executionCanceled()
    }

    override fun onFailure() {
      val error = committer.commitErrors.firstOrNull() ?: return
      showErrorDialog(message("error.executing.commit", taskName, error.localizedMessage), taskName)
    }
  }
}