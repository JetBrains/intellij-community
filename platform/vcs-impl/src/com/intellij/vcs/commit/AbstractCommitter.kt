// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.COMMIT_ACTIVITY
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.vcs.VcsDisposable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.Nls
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class AbstractCommitter(
  project: Project,
  commitMessage: @NlsSafe String,
  private val useCustomPostRefresh: Boolean
) : Committer(project, commitMessage) {
  var currentReporter: RawProgressReporter? = null 

  fun runCommit(taskName: @Nls String, sync: Boolean) {
    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
    if (sync) {
      runWithModalProgressBlocking(project, taskName) {
        delegateCommitToVcsThreadSuspending()
      }
    }
    else {
      VcsDisposable.getInstance(project).coroutineScope.launch {
        withBackgroundProgress(project, taskName) {
          delegateCommitToVcsThreadSuspending()
        }
      }
    }
  }

  protected abstract fun commit()

  private suspend fun delegateCommitToVcsThreadSuspending() {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val activity = COMMIT_ACTIVITY.started(project)

    vcsManager.startBackgroundVcsOperation()
    reportRawProgress { reporter ->
      currentReporter = reporter      
      try {
        progress(message("message.text.background.tasks"))

        // Bridge the updater-thread runnable into a suspend world
        suspendCancellableCoroutine { cont ->
          ChangeListManagerImpl.getInstanceImpl(project).executeOnUpdaterThread {
            progress(message("message.text.commit.progress"))
            try {
              // No ProgressManager.runProcess here — we are already inside a coroutine progress context with the `currentReporter`
              runCommitTask(useCustomPostRefresh) {
                commit()
              }
              cont.resume(Unit)
            }
            catch (t: Throwable) {
              cont.resumeWithException(t)
            }
          }
        }
      }
      finally {
        vcsManager.stopBackgroundVcsOperation()
        activity.finished()
        currentReporter = null
      }
    }
  }

  internal fun progress(message: @Nls String) = currentReporter?.also { 
    it.text(message)
  }
}