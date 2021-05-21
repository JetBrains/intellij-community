// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Experimental

package com.intellij.openapi.application

import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * except it doesn't affect any write actions.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock.
 * If the write action happens while the [action] is running, then the [action] is canceled,
 * and the function suspends until its possible to acquire the read lock, and then the [action] is tried again.
 *
 * Since the [action] might me executed several times, it must be idempotent.
 * The function returns when given [action] was completed fully.
 * [CoroutineContext] passed to the action must be used to check for cancellation inside the [action].
 */
suspend fun <T> readAction(action: (ctx: CoroutineContext) -> T): T {
  return constrainedReadAction(ReadConstraints.unconstrained(), action)
}

/**
 * Suspends until it's possible to obtain the read lock in smart mode and then runs the [action] holding the lock.
 * @see readAction
 */
suspend fun <T> smartReadAction(project: Project, action: (ctx: CoroutineContext) -> T): T {
  return constrainedReadAction(ReadConstraints.inSmartMode(project), action)
}

/**
 * Suspends until it's possible to obtain the read lock with all [constraints] [satisfied][ContextConstraint.isCorrectContext]
 * and then runs the [action] holding the lock.
 * @see readAction
 */
suspend fun <T> constrainedReadAction(constraints: ReadConstraints, action: (ctx: CoroutineContext) -> T): T {
  val application: ApplicationEx = ApplicationManager.getApplication() as ApplicationEx
  check(!application.isDispatchThread) {
    "Must not call from EDT"
  }
  if (application.isReadAccessAllowed) {
    val unsatisfiedConstraint = constraints.findUnsatisfiedConstraint()
    check(unsatisfiedConstraint == null) {
      "Cannot suspend until constraints are satisfied while holding read lock: $unsatisfiedConstraint"
    }
    return action(coroutineContext)
  }
  return supervisorScope {
    readLoop(application, constraints, action)
  }
}

private suspend fun <T> readLoop(application: ApplicationEx, constraints: ReadConstraints, action: (ctx: CoroutineContext) -> T): T {
  while (true) {
    coroutineContext.ensureActive()
    if (application.isWriteActionPending || application.isWriteActionInProgress) {
      yieldToPendingWriteActions() // Write actions are executed on the write thread => wait until write action is processed.
    }
    try {
      when (val readResult = tryReadAction(application, constraints, action)) {
        is ReadResult.Successful -> return readResult.value
        is ReadResult.UnsatisfiedConstraint -> readResult.waitForConstraint.join()
      }
    }
    catch (e: CancellationException) {
      continue // retry
    }
  }
}

private suspend fun <T> tryReadAction(application: ApplicationEx,
                                      constraints: ReadConstraints,
                                      action: (ctx: CoroutineContext) -> T): ReadResult<T> {
  val loopContext = coroutineContext
  return withContext(CoroutineName("read action")) {
    val readCtx: CoroutineContext = this@withContext.coroutineContext
    val readJob: Job = requireNotNull(readCtx[Job])
    val cancellation = {
      readJob.cancel()
    }
    lateinit var result: ReadResult<T>
    runActionAndCancelBeforeWrite(application, cancellation) {
      readJob.ensureActive()
      application.tryRunReadAction {
        val unsatisfiedConstraint = constraints.findUnsatisfiedConstraint()
        result = if (unsatisfiedConstraint == null) {
          ReadResult.Successful(action(readCtx))
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

private fun waitForConstraint(ctx: CoroutineContext, constraint: ContextConstraint): Job {
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

/**
 * Suspends until dumb mode is over and runs [action] in Smart Mode on EDT
 */
suspend fun <T> smartAction(project: Project, action: (ctx: CoroutineContext) -> T): T {
  return suspendCancellableCoroutine { continuation ->
    DumbService.getInstance(project).runWhenSmart(SmartRunnable(action, continuation))
  }
}

private class SmartRunnable<T>(action: (ctx: CoroutineContext) -> T, continuation: CancellableContinuation<T>) : Runnable {

  @Volatile
  private var myAction: ((ctx: CoroutineContext) -> T)? = action

  @Volatile
  private var myContinuation: CancellableContinuation<T>? = continuation

  init {
    continuation.invokeOnCancellation {
      myAction = null
      myContinuation = null // it's not possible to unschedule the runnable, so we make it do nothing instead
    }
  }

  override fun run() {
    val continuation = myContinuation ?: return
    val action = myAction ?: return
    continuation.resumeWith(kotlin.runCatching { action.invoke(continuation.context) })
  }
}