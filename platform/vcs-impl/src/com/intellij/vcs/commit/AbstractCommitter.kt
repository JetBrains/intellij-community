// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.COMMIT_ACTIVITY
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.annotations.Nls

abstract class AbstractCommitter(
  project: Project,
  commitMessage: @NlsSafe String,
  private val useCustomPostRefresh: Boolean
) : Committer(project, commitMessage) {

  fun runCommit(taskName: @Nls String, sync: Boolean) {
    if (sync) {
      runModalTask(taskName, project, true) {
        delegateCommitToVcsThread()
      }
    }
    else {
      runBackgroundableTask(taskName, project, true) {
        delegateCommitToVcsThread()
      }
    }
  }

  protected abstract fun commit()

  private fun delegateCommitToVcsThread() {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val activity = COMMIT_ACTIVITY.started(project)
    vcsManager.startBackgroundVcsOperation()
    try {
      val indicator = DelegatingProgressIndicator()
      TransactionGuard.getInstance().assertWriteSafeContext(indicator.modalityState)
      val endSemaphore = Semaphore()
      endSemaphore.down()

      indicator.text = message("message.text.background.tasks")
      ChangeListManagerImpl.getInstanceImpl(project).executeOnUpdaterThread {
        indicator.text = message("message.text.commit.progress")
        try {
          ProgressManager.getInstance().runProcess(
            {
              runCommitTask(useCustomPostRefresh) {
                commit()
              }
            }, indicator)
        }
        finally {
          endSemaphore.up()
        }
      }

      ProgressIndicatorUtils.awaitWithCheckCanceled(endSemaphore, indicator)
    }
    finally {
      vcsManager.stopBackgroundVcsOperation()
      activity.finished()
    }
  }

  companion object {
    internal fun progress(message: @Nls String) =
      ProgressManager.getInstance().progressIndicator?.apply {
        text = message
        text2 = ""
      }
  }
}