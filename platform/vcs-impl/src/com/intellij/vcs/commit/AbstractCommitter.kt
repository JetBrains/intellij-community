// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.COMMIT_ACTIVITY
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.containers.forEachLoggingErrors
import org.jetbrains.annotations.Nls

abstract class AbstractCommitter(
  val project: Project,
  val commitMessage: @NlsSafe String,
  val commitContext: CommitContext
) {
  private val resultHandlers = mutableListOf<CommitResultHandler>()

  private val _exceptions = mutableListOf<VcsException>()

  val exceptions: List<VcsException> get() = _exceptions.toList()

  val configuration: VcsConfiguration = VcsConfiguration.getInstance(project)

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  fun runCommit(taskName: @Nls String, sync: Boolean) {
    val task = object : Task.Backgroundable(project, taskName, true) {
      override fun run(indicator: ProgressIndicator) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val activity = COMMIT_ACTIVITY.started(myProject) // NON-NLS
        vcsManager.startBackgroundVcsOperation()
        try {
          delegateCommitToVcsThread()
        }
        finally {
          vcsManager.stopBackgroundVcsOperation()
          activity.finished()
        }
      }

      override fun shouldStartInBackground(): Boolean = !sync && super.shouldStartInBackground()

      override fun isConditionalModal(): Boolean = sync
    }

    task.queue()
  }

  protected abstract fun commit()

  protected abstract fun onSuccess()

  protected abstract fun onFailure()

  protected abstract fun onFinish()

  private fun delegateCommitToVcsThread() {
    val indicator = DelegatingProgressIndicator()
    TransactionGuard.getInstance().assertWriteSafeContext(indicator.modalityState)
    val endSemaphore = Semaphore()

    endSemaphore.down()
    ChangeListManagerImpl.getInstanceImpl(project).executeOnUpdaterThread {
      indicator.text = message("message.text.commit.progress")
      try {
        ProgressManager.getInstance().runProcess(
          {
            indicator.checkCanceled()
            doRunCommit()
          }, indicator)
      }
      catch (ignored: ProcessCanceledException) {
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
      finally {
        endSemaphore.up()
      }
    }

    indicator.text = message("message.text.background.tasks")
    ProgressIndicatorUtils.awaitWithCheckCanceled(endSemaphore, indicator)
  }

  private fun doRunCommit() {
    var canceled = false
    try {
      commit()
    }
    catch (e: ProcessCanceledException) {
      canceled = true
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      addException(e)
    }
    finally {
      finishCommit(canceled)
      onFinish()
    }
  }

  protected fun Throwable.asVcsException(): VcsException = if (this is VcsException) this else VcsException(this)

  protected fun addException(e: Throwable) {
    _exceptions.add(e.asVcsException())
  }

  private fun finishCommit(canceled: Boolean) {
    val errors = collectErrors(_exceptions)
    val noErrors = errors.isEmpty()

    if (canceled) {
      resultHandlers.forEachLoggingErrors(LOG) { it.onCancel() }
    }
    else if (noErrors) {
      resultHandlers.forEachLoggingErrors(LOG) { it.onSuccess(commitMessage) }
      onSuccess()
    }
    else {
      resultHandlers.forEachLoggingErrors(LOG) { it.onFailure(errors) }
      onFailure()
    }
  }

  companion object {
    private val LOG = logger<AbstractCommitter>()

    @JvmStatic
    fun collectErrors(exceptions: List<VcsException>): List<VcsException> = exceptions.filterNot { it.isWarning }

    internal fun progress(message: @Nls String) =
      ProgressManager.getInstance().progressIndicator?.apply {
        text = message
        text2 = ""
      }
  }
}