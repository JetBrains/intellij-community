// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.forEachLoggingErrors

abstract class Committer(
  val project: Project,
  val commitMessage: @NlsSafe String,
) {
  private val resultHandlers = mutableListOf<CommitResultHandler>()

  private val _exceptions = mutableListOf<VcsException>()

  val exceptions: List<VcsException> get() = _exceptions.toList()
  val commitErrors: List<VcsException> get() = collectErrors(_exceptions)

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  protected fun addException(e: Throwable) {
    _exceptions.add(e.asVcsException())
  }

  @RequiresBackgroundThread
  protected fun runCommitTask(task: () -> Unit) {
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
      finishCommit(canceled)
    }
  }

  private fun finishCommit(canceled: Boolean) {
    val errors = commitErrors

    if (canceled) {
      LOG.debug("Commit canceled")
      resultHandlers.forEachLoggingErrors(LOG) { it.onCancel() }
    }
    else if (errors.isEmpty()) {
      LOG.debug("Commit successful")
      resultHandlers.forEachLoggingErrors(LOG) { it.onSuccess(commitMessage) }
    }
    else {
      LOG.debug("Commit failed")
      resultHandlers.forEachLoggingErrors(LOG) { it.onFailure(errors) }
    }
  }

  companion object {
    private val LOG = logger<Committer>()

    @JvmStatic
    fun collectErrors(exceptions: List<VcsException>): List<VcsException> = exceptions.filterNot { it.isWarning }

    fun Throwable.asVcsException(): VcsException = if (this is VcsException) this else VcsException(this)
  }
}
