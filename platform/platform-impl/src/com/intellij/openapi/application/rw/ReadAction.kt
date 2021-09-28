// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadConstraints
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.withJob
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal class ReadAction<T>(
  private val constraints: ReadConstraints,
  private val blocking: Boolean,
  private val action: () -> T
) {

  private val application: ApplicationEx = ApplicationManager.getApplication() as ApplicationEx

  suspend fun runReadAction(): T {
    check(!application.isDispatchThread) {
      "Must not call from EDT"
    }
    if (application.isReadAccessAllowed) {
      val unsatisfiedConstraint = constraints.findUnsatisfiedConstraint()
      check(unsatisfiedConstraint == null) {
        "Cannot suspend until constraints are satisfied while holding the read lock: $unsatisfiedConstraint"
      }
      return withJob(coroutineContext.job, action)
    }
    return coroutineScope {
      readLoop(this)
    }
  }

  private suspend fun readLoop(rootScope: CoroutineScope): T {
    while (true) {
      rootScope.coroutineContext.ensureActive()
      if (application.isWriteActionPending || application.isWriteActionInProgress) {
        yieldToPendingWriteActions() // Write actions are executed on the write thread => wait until write action is processed.
      }
      when (val readResult = tryReadAction(rootScope)) {
        is ReadResult.Successful -> return readResult.value
        is ReadResult.UnsatisfiedConstraint -> readResult.waitForConstraint.join()
        is ReadResult.WritePending -> Unit // retry
      }
    }
  }

  private suspend fun tryReadAction(rootScope: CoroutineScope): ReadResult<T> {
    if (blocking) {
      return tryReadAction(rootScope, rootScope.coroutineContext.job)
             ?: ReadResult.WritePending
    }
    var result: ReadResult<T>? = null
    rootScope.launch(CoroutineName("read action")) {
      val readJob = coroutineContext.job
      ProgressIndicatorUtils.runActionAndCancelBeforeWrite(application, readJob::cancel) {
        readJob.ensureActive()
        result = tryReadAction(rootScope, readJob)
      }
    }.join()
    return result
           ?: ReadResult.WritePending
  }

  private fun tryReadAction(rootScope: CoroutineScope, readJob: Job): ReadResult<T>? {
    var result: ReadResult<T>? = null
    application.tryRunReadAction {
      val unsatisfiedConstraint = constraints.findUnsatisfiedConstraint()
      result = if (unsatisfiedConstraint == null) {
        ReadResult.Successful(withJob(readJob, action))
      }
      else {
        ReadResult.UnsatisfiedConstraint(waitForConstraint(rootScope, unsatisfiedConstraint))
      }
    }
    return result
  }
}

private sealed class ReadResult<out T> {
  class Successful<T>(val value: T) : ReadResult<T>()
  class UnsatisfiedConstraint(val waitForConstraint: Job) : ReadResult<Nothing>()
  object WritePending : ReadResult<Nothing>()
}

/**
 * Suspends the execution until the write thread queue is processed.
 */
private suspend fun yieldToPendingWriteActions() {
  // the runnable is executed on the write thread _after_ the current or pending write action
  yieldUntilRun(ApplicationManager.getApplication()::invokeLater)
}

private fun waitForConstraint(rootScope: CoroutineScope, constraint: ConstrainedExecution.ContextConstraint): Job {
  return rootScope.launch(Dispatchers.Unconfined + CoroutineName("waiting for constraint '$constraint'")) {
    check(ApplicationManager.getApplication().isReadAccessAllowed) // schedule while holding read lock
    yieldUntilRun(constraint::schedule)
    check(constraint.isCorrectContext())
    // Job is finished, readLoop may continue the next attempt
  }
}

private suspend fun yieldUntilRun(schedule: (Runnable) -> Unit) {
  suspendCancellableCoroutine<Unit> { continuation ->
    schedule(ResumeContinuationRunnable(continuation))
  }
}

private class ResumeContinuationRunnable(continuation: CancellableContinuation<Unit>) : Runnable {

  @Volatile
  private var myContinuation: CancellableContinuation<Unit>? = continuation

  init {
    continuation.invokeOnCancellation {
      myContinuation = null // it's not possible to unschedule the runnable, so we make it do nothing instead
    }
  }

  override fun run() {
    myContinuation?.resume(Unit)
  }
}
