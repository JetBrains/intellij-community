// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.forEachLoggingErrors
import org.jetbrains.annotations.ApiStatus
import java.util.*

abstract class Committer(
  val project: Project,
  val commitMessage: @NlsSafe String,
) {
  private val resultHandlers = mutableListOf<CommitterResultHandler>()

  private val _exceptions = mutableListOf<VcsException>()

  val exceptions: List<VcsException> get() = _exceptions.toList()
  val commitErrors: List<VcsException> get() = collectErrors(_exceptions)

  fun addResultHandler(resultHandler: CommitterResultHandler) {
    resultHandlers += resultHandler
  }

  protected fun addException(e: Throwable) {
    _exceptions.add(e.asVcsException())
  }

  @RequiresBackgroundThread
  protected fun runCommitTask(useCustomPostRefresh: Boolean, task: () -> Unit) {
    var canceled = false
    try {
      ProgressManager.checkCanceled()
      task()
    }
    catch (e: ProcessCanceledException) {
      canceled = true
    }
    catch (e: Throwable) {
      LOG.warn(e)
      addException(e)
    }
    finally {
      runInEdt {
        finishCommit(useCustomPostRefresh, canceled)
      }
    }
  }

  private fun finishCommit(useCustomPostRefresh: Boolean, canceled: Boolean) {
    val errors = commitErrors

    if (canceled) {
      LOG.debug("Commit canceled")
      resultHandlers.forEachLoggingErrors(LOG) { it.onCancel() }
    }
    else if (errors.isEmpty()) {
      LOG.debug("Commit successful")
      resultHandlers.forEachLoggingErrors(LOG) { it.onSuccess() }
    }
    else {
      LOG.debug("Commit failed")
      resultHandlers.forEachLoggingErrors(LOG) { it.onFailure() }
    }

    if (!useCustomPostRefresh) {
      fireAfterRefresh()
    }
  }

  protected fun fireAfterRefresh() {
    resultHandlers.forEachLoggingErrors(LOG) { it.onAfterRefresh() }
  }

  companion object {
    private val LOG = logger<Committer>()

    @JvmStatic
    fun collectErrors(exceptions: List<VcsException>): List<VcsException> = exceptions.filterNot { it.isWarning }

    fun Throwable.asVcsException(): VcsException = if (this is VcsException) this else VcsException(this)
  }
}

interface CommitterResultHandler : EventListener {
  @RequiresEdt
  fun onSuccess() {
  }

  @RequiresEdt
  fun onCancel() {
  }

  @RequiresEdt
  fun onFailure() {
  }

  /**
   * 'onFinally' that might be delayed until VFS/CLM refreshes are done.
   */
  @RequiresEdt
  fun onAfterRefresh() {
  }
}

@ApiStatus.Internal
class CommitResultHandlerNotifier(private val committer: Committer,
                                  private val handlers: List<CommitResultHandler>) : CommitterResultHandler {
  constructor(committer: Committer, handler: CommitResultHandler) : this(committer, listOf(handler))

  override fun onSuccess() {
    val commitMessage = committer.commitMessage
    handlers.forEachLoggingErrors(LOG) { it.onSuccess(commitMessage) }
  }

  override fun onCancel() {
    handlers.forEachLoggingErrors(LOG) { it.onCancel() }
  }

  override fun onFailure() {
    val errors = committer.commitErrors
    handlers.forEachLoggingErrors(LOG) { it.onFailure(errors) }
  }

  companion object {
    private val LOG = logger<CommitResultHandlerNotifier>()
  }
}
