// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.application.constraints.BaseConstrainedExecution.ReschedulingRunnable
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Runnable
import java.util.function.BooleanSupplier
import kotlin.coroutines.ContinuationInterceptor

/**
 * This class is responsible for running a task in a proper context defined using various builder methods of this class and it's
 * implementations, like [com.intellij.openapi.application.AppUIExecutor.later], or generic [withConstraint].
 *
 * ## Implementation notes: ##
 *
 * The [scheduleWithinConstraints] starts checking the list of constraints, one by one, rescheduling and restarting itself
 * for each unsatisfied constraint ([ReschedulingRunnable]), until at some point *all* of the constraints are satisfied *at once*.
 *
 * This ultimately ends up with [ContextConstraint.schedule] being called one by one for every constraint that needs to be scheduled.
 * Finally, the runnable is called, executing the task in the properly arranged context.
 *
 * @author eldar
 * @author peter
 */
abstract class BaseConstrainedExecution<E : ConstrainedExecution<E>>(protected val constraints: Array<ContextConstraint>)
  : ConstrainedExecution<E>, ConstrainedExecutionScheduler {
  protected abstract fun cloneWith(constraints: Array<ContextConstraint>): E

  override fun withConstraint(constraint: ContextConstraint): E = cloneWith(constraints + constraint)

  override fun asExecutor() = ConstrainedTaskExecutor(this, composeExpiration())
  override fun asCoroutineDispatcher(): ContinuationInterceptor = createConstrainedCoroutineDispatcher(this, composeExpiration())
  protected open fun composeExpiration(): Expiration? = null

  override fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier?) = doSchedule(runnable, condition, null)

  private fun doSchedule(runnable: Runnable, condition: BooleanSupplier?,
                         previousAttempt: ReschedulingAttempt?) {
    if (condition?.asBoolean == false) return
    for (constraint in constraints) {
      if (!constraint.isCorrectContext()) {
        return constraint.schedule(ReschedulingRunnable(runnable, condition, constraint, previousAttempt))
      }
    }
    runnable.run()
  }

  private inner class ReschedulingRunnable(private val runnable: Runnable,
                                           private val condition: BooleanSupplier?,
                                           constraint: ContextConstraint,
                                           previousAttempt: ReschedulingAttempt?) : ReschedulingAttempt(constraint,
                                                                                                        previousAttempt), Runnable {
    override fun run() {
      LOG.assertTrue(constraint.isCorrectContext())
      doSchedule(runnable, condition, previousAttempt = this)
    }

    override fun toString(): String = "$runnable rescheduled due to " + super.toString()
  }

  private open class ReschedulingAttempt(val constraint: ContextConstraint,
                                         private val previousAttempt: ReschedulingAttempt?) {
    private val attemptChain: Sequence<ReschedulingAttempt> get() = generateSequence(this) { it.previousAttempt }
    private val attemptNumber: Int = ((previousAttempt?.attemptNumber ?: 0) + 1).also { n ->
      if (n > 3000) {
        val lastConstraints = attemptChain.take(15).map { it.constraint }
        LOG.error("Too many reschedule requests, probably constraints can't be satisfied all together: " + lastConstraints.joinToString())
      }
    }

    override fun toString(): String {
      val limit = 5
      val lastConstraints = attemptChain.take(limit).mapTo(mutableListOf()) { "[${it.attemptNumber}]${it.constraint}" }
      if (lastConstraints.size == limit) lastConstraints[limit - 1] = "..."
      return lastConstraints.joinToString(" <- ")
    }
  }

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.constraints.ConstrainedExecution")
  }
}