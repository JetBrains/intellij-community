// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.internal.statistic.IdeActivity
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList
import org.jetbrains.annotations.Nls

private val LOG = logger<AbstractCommitter>()

abstract class AbstractCommitter(
  val project: Project,
  val changes: List<Change>,
  val commitMessage: @NlsSafe String,
  val commitContext: CommitContext
) {
  private val resultHandlers = createLockFreeCopyOnWriteList<CommitResultHandler>()

  private val _feedback = mutableSetOf<String>()
  private val _failedToCommitChanges = mutableListOf<Change>()
  private val _exceptions = mutableListOf<VcsException>()
  private val _pathsToRefresh = mutableListOf<FilePath>()

  val feedback: Set<String> get() = _feedback.toSet()
  val failedToCommitChanges: List<Change> get() = _failedToCommitChanges.toList()
  val exceptions: List<VcsException> get() = _exceptions.toList()
  val pathsToRefresh: List<FilePath> get() = _pathsToRefresh.toList()

  val configuration: VcsConfiguration = VcsConfiguration.getInstance(project)

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  fun runCommit(taskName: @Nls String, sync: Boolean) {
    val task = object : Task.Backgroundable(project, taskName, true, configuration.commitOption) {
      override fun run(indicator: ProgressIndicator) {
        val vcsManager = ProjectLevelVcsManager.getInstance(myProject)
        val activity = IdeActivity.started(myProject, "vcs", "commit") // NON-NLS
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

  protected abstract fun afterCommit()

  protected abstract fun onSuccess()

  protected abstract fun onFailure()

  protected abstract fun onFinish()

  protected fun commit(vcs: AbstractVcs, changes: List<Change>) {
    val environment = vcs.checkinEnvironment
    if (environment != null) {
      _pathsToRefresh.addAll(ChangesUtil.getPaths(changes))
      val exceptions = environment.commit(changes, commitMessage, commitContext, _feedback)
      if (!exceptions.isNullOrEmpty()) {
        _exceptions.addAll(exceptions)
        _failedToCommitChanges.addAll(changes)
      }
    }
  }

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
      SaveCommittingDocumentsVetoer.run(project, changes) { commit() }
      afterCommit()
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
    val noWarnings = _exceptions.isEmpty()

    if (canceled) {
      resultHandlers.forEach { it.onCancel() }
    }
    else if (noErrors) {
      resultHandlers.forEach { it.onSuccess(commitMessage) }
      onSuccess()

      if (noWarnings) {
        progress(message("commit.dialog.completed.successfully"))
      }
    }
    else {
      resultHandlers.forEach { it.onFailure(errors) }
      onFailure()
    }
  }

  companion object {
    @JvmStatic
    fun collectErrors(exceptions: List<VcsException>): List<VcsException> = exceptions.filterNot { it.isWarning }

    internal fun progress(message: @Nls String) =
      ProgressManager.getInstance().progressIndicator?.apply {
        text = message
        text2 = ""
      }
  }
}