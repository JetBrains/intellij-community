// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal class InternalReadAction<T>(
  private val constraints: List<ReadConstraint>,
  private val blocking: Boolean,
  private val action: () -> T
) {

  private val application: ApplicationEx = ApplicationManager.getApplication() as ApplicationEx

  suspend fun runReadAction(): T {
    check(!application.isDispatchThread) {
      "Must not call from EDT"
    }
    if (application.isReadAccessAllowed) {
      val unsatisfiedConstraint = findUnsatisfiedConstraint()
      check(unsatisfiedConstraint == null) {
        "Cannot suspend until constraints are satisfied while holding the read lock: $unsatisfiedConstraint"
      }
      return blockingContext(action)
    }
    return coroutineScope {
      readLoop()
    }
  }

  private fun findUnsatisfiedConstraint(): ReadConstraint? {
    for (constraint in constraints) {
      if (!constraint.isSatisfied()) {
        return constraint
      }
    }
    return null
  }

  private suspend fun readLoop(): T {
    val loopJob = coroutineContext.job
    while (true) {
      loopJob.ensureActive()
      if (application.isWriteActionPending || application.isWriteActionInProgress) {
        yieldToPendingWriteActions() // Write actions are executed on the write thread => wait until write action is processed.
      }
      when (val readResult = tryReadAction(loopJob)) {
        is ReadResult.Successful -> return readResult.value
        is ReadResult.UnsatisfiedConstraint -> readResult.waitForConstraint.join()
        is ReadResult.WritePending -> Unit // retry
      }
    }
  }

  private suspend fun tryReadAction(loopJob: Job): ReadResult<T> = blockingContext {
    if (blocking) {
      tryReadBlocking(loopJob)
    }
    else {
      tryReadCancellable(loopJob)
    }
  }

  private fun tryReadBlocking(loopJob: Job): ReadResult<T> {
    var result: ReadResult<T>? = null
    application.tryRunReadAction {
      result = insideReadAction(loopJob)
    }
    return result
           ?: ReadResult.WritePending
  }

  private fun tryReadCancellable(loopJob: Job): ReadResult<T> = try {
    cancellableReadActionInternal(loopJob) {
      insideReadAction(loopJob)
    }
  }
  catch (e: CancellationException) {
    if (e.cause is CannotReadException) {
      ReadResult.WritePending
    }
    else {
      throw e
    }
  }

  private fun insideReadAction(loopJob: Job): ReadResult<T> {
    val unsatisfiedConstraint = findUnsatisfiedConstraint()
    return if (unsatisfiedConstraint == null) {
      ReadResult.Successful(action())
    }
    else {
      ReadResult.UnsatisfiedConstraint(waitForConstraint(loopJob, unsatisfiedConstraint))
    }
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

private fun waitForConstraint(loopJob: Job, constraint: ReadConstraint): Job {
  return CoroutineScope(loopJob).launch(Dispatchers.Unconfined + CoroutineName("waiting for constraint '$constraint'")) {
    check(ApplicationManager.getApplication().isReadAccessAllowed) // schedule while holding read lock
    yieldUntilRun(constraint::schedule)
    check(constraint.isSatisfied())
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
