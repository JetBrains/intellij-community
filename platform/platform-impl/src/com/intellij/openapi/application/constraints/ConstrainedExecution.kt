// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.coroutines.ContinuationInterceptor

/**
 * @author eldar
 */
interface ConstrainedExecution<E : ConstrainedExecution<E>> {
  /**
   * Use the returned dispatcher to run coroutines using the standard [withContext], [launch] and [async].
   *
   * Never use [kotlinx.coroutines.asCoroutineDispatcher] with [asExecutor]: the latter may violate the [Executor] contract.
   */
  fun asCoroutineDispatcher(): ContinuationInterceptor

  /**
   * TL;DR. Never use for scheduling coroutines, use the [asCoroutineDispatcher] instead.
   *
   * If there were any [Disposable]s registered using [expireWith], [withConstraint], or any specialized builder methods implying the
   * execution may "expire" at some point, the returned object violates the contract of the [Executor] interface. Once the execution
   * expires, it refuses to execute a runnable silently, without throwing the required [java.util.concurrent.RejectedExecutionException].
   * While this behavior may seem to be convenient, and matches the behavior of other IDEA execution services taking [Disposable]s,
   * such executor MUST NEVER be used for dispatching coroutines, as execution of a coroutine may hang at a suspension point forever
   * without giving it a chance to handle cancellation and exit gracefully.
   */
  fun asExecutor(): Executor

  /**
   * The [constraint] MUST guarantee to execute a runnable passed to its [ContextConstraint.schedule] method at some point.
   * For dispatchers that may refuse to run the task based on some condition consider the [withConstraint] overload
   * taking a [Disposable] as an argument.
   */
  fun withConstraint(constraint: ContextConstraint): E

  /**
   * Use this method for unreliable dispatchers that don't usually run a task once some [Disposable] is disposed.
   * Is ensures that a coroutine continuation is invoked at some point regardless potential disposal.
   *
   * At the very least, the [constraint] MUST guarantee to execute a runnable passed to its [ContextConstraint.schedule] method
   * if the [parentDisposable] is still not disposed by the time the dispatcher arranges the proper execution context.
   * It is OK to execute it after the expirable has been disposed though.
   */
  fun withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): E

  fun expireWith(parentDisposable: Disposable): E

  /**
   * Execution context is defined using a list of [ContextConstraint]s, with each constraint called to ensure the current context
   * [is correct][isCorrectContext]. Whenever there's a constraint in the list that isn't satisfied, its [schedule] method is called
   * to reschedule another attempt to traverse the list of constraints.
   */
  interface ContextConstraint {
    fun isCorrectContext(): Boolean
    fun schedule(runnable: Runnable)
    override fun toString(): String
  }
}