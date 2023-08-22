// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import java.util.function.BooleanSupplier

/**
 * NB. Methods defined in this interface must be used with great care, this is purely to expose internals required for implementing
 * scheduling methods and coroutine dispatching support.
 *
 * @author eldar
 */
internal interface ConstrainedExecutionScheduler {
  /**
   * The returned executor doesn't take into account expiration state of any of [Disposable]s added using [ConstrainedExecution.expireWith]
   * and [ConstrainedExecution.withConstraint]. Submitted tasks are executed unconditionally, even if those are disposed.
   *
   * NB. Scheduling of the [runnable] stops once a [condition], if any, returns false.
   * While this behavior may seem to be convenient, and matches the behavior of other IDEA execution services taking [Disposable]s,
   * such executor MUST NEVER be used for dispatching coroutines, as execution of a coroutine may hang at a suspension point forever
   * without giving it a chance to handle cancellation and exit gracefully.
   */
  fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier? = null)
}