// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadConstraints
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.progress.JobProgress
import com.intellij.openapi.progress.Progress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal class ReadAction<T>(
  private val constraints: ReadConstraints,
  private val action: (Progress) -> T
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
      return action(JobProgress(coroutineContext.job))
    }
    return supervisorScope {
      readLoop()
    }
  }

  private suspend fun readLoop(): T {
    while (true) {
      coroutineContext.ensureActive()
      if (application.isWriteActionPending || application.isWriteActionInProgress) {
        yieldToPendingWriteActions() // Write actions are executed on the write thread => wait until write action is processed.
      }
      try {
        when (val readResult = tryReadAction()) {
          is ReadResult.Successful -> return readResult.value
          is ReadResult.UnsatisfiedConstraint -> readResult.waitForConstraint.join()
        }
      }
      catch (e: CancellationException) {
        continue // retry
      }
    }
  }

  private suspend fun tryReadAction(): ReadResult<T> {
    val loopContext = coroutineContext
    return withContext(CoroutineName("read action")) {
      val readJob: Job = this@withContext.coroutineContext.job
      val cancellation = {
        readJob.cancel()
      }
      lateinit var result: ReadResult<T>
      ProgressIndicatorUtils.runActionAndCancelBeforeWrite(application, cancellation) {
        readJob.ensureActive()
        application.tryRunReadAction {
          val unsatisfiedConstraint = constraints.findUnsatisfiedConstraint()
          result = if (unsatisfiedConstraint == null) {
            ReadResult.Successful(action(JobProgress(readJob)))
          }
          else {
            ReadResult.UnsatisfiedConstraint(waitForConstraint(loopContext, unsatisfiedConstraint))
          }
        }
      }
      readJob.ensureActive()
      result
    }
  }
}

private sealed class ReadResult<T> {
  class Successful<T>(val value: T) : ReadResult<T>()
  class UnsatisfiedConstraint<T>(val waitForConstraint: Job) : ReadResult<T>()
}

/**
 * Suspends the execution until the write thread queue is processed.
 */
private suspend fun yieldToPendingWriteActions() {
  // the runnable is executed on the write thread _after_ the current or pending write action
  yieldUntilRun(ApplicationManager.getApplication()::invokeLater)
}

private fun waitForConstraint(ctx: CoroutineContext, constraint: ConstrainedExecution.ContextConstraint): Job {
  return CoroutineScope(ctx).launch(Dispatchers.Unconfined + CoroutineName("waiting for constraint '$constraint'")) {
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
