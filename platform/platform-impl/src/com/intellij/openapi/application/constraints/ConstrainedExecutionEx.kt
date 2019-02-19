// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier

/**
 * NB. Methods defined in this interface must be used with great care, this is purely to expose internals required for implementing
 * scheduling methods and coroutine dispatching support.
 *
 * Consider using high-level [ConstrainedTaskExecutor.execute] and [ConstrainedTaskExecutor.submit] extension methods for scheduling
 * runnables, and [launch], [async], [withContext] with the [ConstrainedCoroutineSupport.coroutineContext] for running coroutines.
 *
 * @author eldar
 */
internal interface ConstrainedExecutionEx<E : ConstrainedExecution<E>> : ConstrainedExecution<E> {
  /**
   * The returned executor doesn't take into account expiration state of any of [Disposable]s added using [expireWith] and [withConstraint].
   * Submitted tasks are executed unconditionally, even if those are [disposed][Disposer.isDisposed].
   *
   * NB. Using a [condition] returning false makes the executor violate the contract of [Executor.execute]:
   * it may refuse to execute a runnable silently, without throwing the required [java.util.concurrent.RejectedExecutionException].
   * While this behavior may seem to be convenient, and matches the behavior of other IDEA execution services taking [Disposable]s,
   * such executor MUST NEVER be used for dispatching coroutines, as execution of a coroutine may hang at a suspension point forever
   * without giving it a chance to handle cancellation and exit gracefully.
   */
  fun createConstraintSchedulingExecutor(condition: BooleanSupplier? = null): Executor

  fun dispatchLaterUnconstrained(runnable: Runnable)

  /**
   * Returns [Expiration] corresponding to the set of [Disposable]s added using [expireWith] and [withConstraint], if any.
   */
  fun composeExpiration(): Expiration?
}