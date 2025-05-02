// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.getGlobalThreadingSupport
import com.intellij.openapi.application.isLockStoredInContext
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal class InternalReadAction<T>(
  private val constraints: List<ReadConstraint>,
  private val undispatched: Boolean,
  private val blocking: Boolean,
  private val action: () -> T
) {

  private val application: ApplicationEx = ApplicationManager.getApplication() as ApplicationEx

  suspend fun runReadAction(): T {
    return if (undispatched) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      if (application.isReadAccessAllowed) {
        val unsatisfiedConstraint = findUnsatisfiedConstraint()
        check(unsatisfiedConstraint == null) {
          "Cannot suspend until constraints are satisfied while holding the read lock: $unsatisfiedConstraint"
        }
        return blockingContext {
          // To copy permit from context to thread local
          ReadAction.compute<T, Throwable>(action)
        }
      }
      coroutineScope {
        readLoop()
      }
    }
    else {
      // Third condition is check for lock consistency
      if (isLockStoredInContext && application.isParallelizedReadAction(currentCoroutineContext()) && application.isReadAccessAllowed) {
        val unsatisfiedConstraint = findUnsatisfiedConstraint()
        check(unsatisfiedConstraint == null) {
          "Cannot suspend until constraints are satisfied while holding the read lock: $unsatisfiedConstraint"
        }
        return withContext(Dispatchers.Default) {
          // To copy permit from context to thread local
          ReadAction.compute<T, Throwable>(action)
        }
      }
      withContext(Dispatchers.Default) {
        application.assertReadAccessNotAllowed()
        readLoop()
      }
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
      when (val readResult = tryReadAction()) {
        is ReadResult.Successful -> return readResult.value
        is ReadResult.UnsatisfiedConstraint -> readResult.constraint.awaitConstraint()
        is ReadResult.WritePending -> Unit // retry
      }
    }
  }

  private suspend fun tryReadAction(): ReadResult<T> {
    return if (blocking) {
      tryReadBlocking()
    }
    else {
      tryReadCancellable()
    }
  }

  private suspend fun tryReadBlocking(): ReadResult<T> {
    return blockingContext {
      var result: ReadResult<T>? = null
      application.tryRunReadAction {
        result = insideReadAction()
      }
      result ?: ReadResult.WritePending
    }
  }

  private suspend fun tryReadCancellable(): ReadResult<T> = try {
    val ctx = currentCoroutineContext()
    cancellableReadActionInternal(ctx) {
      insideReadAction()
    }
  }
  catch (readCe: CannotReadException) {
    ReadResult.WritePending
  }

  private fun insideReadAction(): ReadResult<T> {
    val unsatisfiedConstraint = findUnsatisfiedConstraint()
    return if (unsatisfiedConstraint == null) {
      ReadResult.Successful(action())
    }
    else {
      ReadResult.UnsatisfiedConstraint(unsatisfiedConstraint)
    }
  }
}

private sealed class ReadResult<out T> {
  class Successful<T>(val value: T) : ReadResult<T>()
  class UnsatisfiedConstraint(val constraint: ReadConstraint) : ReadResult<Nothing>()
  object WritePending : ReadResult<Nothing>()
}

/**
 * Suspends the execution until the write thread queue is processed.
 */
private suspend fun yieldToPendingWriteActions() {
  // the runnable is executed on the write thread _after_ the current or pending write action
  yieldUntilRun { runnable ->
    getGlobalThreadingSupport().runWhenWriteActionIsCompleted {
      runnable.run()
    }
  }
}

internal suspend fun yieldUntilRun(schedule: (Runnable) -> Unit) {
  suspendCancellableCoroutine { continuation ->
    schedule(ResumeContinuationRunnable(continuation))
  }
}

private class ResumeContinuationRunnable(continuation: CancellableContinuation<Unit>) : ContextAwareRunnable {

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
