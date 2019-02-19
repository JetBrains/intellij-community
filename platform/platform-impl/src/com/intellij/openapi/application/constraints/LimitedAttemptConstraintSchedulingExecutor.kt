// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import kotlinx.coroutines.Runnable
import java.util.*
import java.util.function.BooleanSupplier

internal class LimitedAttemptConstraintSchedulingExecutor(constraints: Array<ContextConstraint>,
                                                          condition: BooleanSupplier?,
                                                          private val myLimit: Int = 3000)
  : BaseConstrainedExecution.ConditionalConstraintSchedulingExecutor(constraints, condition) {
  private var myAttemptCount: Int = 0

  private val myLogLimit: Int = 30
  private val myLastConstraints: Deque<ContextConstraint> = ArrayDeque(myLogLimit)

  override fun execute(runnable: Runnable) {
    resetAttemptCount()
    super.execute(runnable)
  }

  override fun retrySchedule(runnable: Runnable, causeConstraint: ContextConstraint) {
    if (checkHaveMoreRescheduleAttempts(causeConstraint)) {
      super.execute(runnable)
    }
    else {
      BaseConstrainedExecution.LOG.error(TooManyRescheduleAttemptsException(myLastConstraints))
    }
  }

  private fun resetAttemptCount() {
    myLastConstraints.clear()
    myAttemptCount = 0
  }

  private fun checkHaveMoreRescheduleAttempts(constraint: ContextConstraint): Boolean {
    with(myLastConstraints) {
      if (isNotEmpty() && size >= myLogLimit) removeFirst()
      addLast(constraint)
    }
    return ++myAttemptCount < myLimit
  }

  /**
   * Thrown at a cancellation point when the executor is unable to arrange the requested context after a reasonable number of attempts.
   *
   * WARNING: The exception thrown is handled in a fallback context as a last resort,
   *          The fallback context is EDT with a proper modality state, no other guarantee is made.
   */
  internal class TooManyRescheduleAttemptsException internal constructor(lastConstraints: Deque<ContextConstraint>)
    : Exception("Too many reschedule requests, probably constraints can't be satisfied all together: " + lastConstraints.joinToString())
}