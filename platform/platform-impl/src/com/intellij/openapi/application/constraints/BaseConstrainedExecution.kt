// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.application.constraints.BaseConstrainedExecution.ReschedulingRunnable
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Runnable
import java.util.function.BooleanSupplier
import java.util.function.Consumer

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

  override fun asExecutor() = ConstrainedTaskExecutor(this, composeCancellationCondition(), composeExpiration())
  override fun asCoroutineDispatcher() = createConstrainedCoroutineDispatcher(this, composeCancellationCondition(), composeExpiration())
  protected open fun composeExpiration(): Expiration? = null
  protected open fun composeCancellationCondition(): BooleanSupplier? = null

  override fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier?) =
    doScheduleWithinConstraints(Consumer { runnable.run() }, condition, ReschedulingAttempt.NULL)

  @JvmOverloads
  protected fun doScheduleWithinConstraints(task: Consumer<ReschedulingAttempt>,
                                            condition: BooleanSupplier? = null,
                                            previousAttempt: ReschedulingAttempt) {
    if (condition?.asBoolean == false) return
    for (constraint in constraints) {
      if (!constraint.isCorrectContext()) {
        return constraint.schedule(ReschedulingRunnable(task, condition, constraint, previousAttempt))
      }
    }
    task.accept(previousAttempt)
  }

  private inner class ReschedulingRunnable(private val task: Consumer<ReschedulingAttempt>,
                                           private val condition: BooleanSupplier?,
                                           private val constraint: ContextConstraint,
                                           previousAttempt: ReschedulingAttempt) : ReschedulingAttempt(constraint,
                                                                                                       previousAttempt), Runnable {
    override fun run() {
      LOG.assertTrue(constraint.isCorrectContext())
      doScheduleWithinConstraints(task = task, condition = condition, previousAttempt = this)
    }

    override fun toString(): String = "$task rescheduled due to " + super.toString()
  }

  protected open class ReschedulingAttempt private constructor(private val cause: Any?,
                                                               private val previousAttempt: ReschedulingAttempt?,
                                                               private val attemptNumber: Int) {
    private val attemptChain: Sequence<ReschedulingAttempt> get() = generateSequence(this) { it.previousAttempt }

    init {
      if (attemptNumber > 3000) {
        val lastCauses = attemptChain.take(15).map { it.cause }
        LOG.error("Too many reschedule requests, probably constraints can't be satisfied all together: " + lastCauses.joinToString())
      }
    }

    constructor(cause: Any, previousAttempt: ReschedulingAttempt) : this(cause, previousAttempt,
                                                                         attemptNumber = previousAttempt.attemptNumber + 1)

    override fun toString(): String {
      val limit = 5
      val lastCauses = attemptChain.take(limit).mapTo(mutableListOf()) { "[${it.attemptNumber}]${it.cause}" }
      if (lastCauses.size == limit) lastCauses[limit - 1] = "..."
      return lastCauses.joinToString(" <- ")
    }

    companion object {
      @JvmField
      val NULL = ReschedulingAttempt(cause = null, previousAttempt = null, attemptNumber = 0)
    }
  }

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.constraints.ConstrainedExecution")
  }
}