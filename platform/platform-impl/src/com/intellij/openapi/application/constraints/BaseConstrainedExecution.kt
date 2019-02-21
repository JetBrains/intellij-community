// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.application.constraints.BaseConstrainedExecution.ConstraintSchedulingExecutor
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Runnable
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier
import kotlin.coroutines.ContinuationInterceptor

/**
 * This class is responsible for running a task in a proper context defined using various builder methods of this class and it's
 * implementations, like [com.intellij.openapi.application.AppUIExecutor.later], or generic [withConstraint].
 *
 * ## Implementation notes: ##
 *
 * So, the [ConstraintSchedulingExecutor.execute] starts checking the list of constraints, one by one, rescheduling and restarting itself
 * for each unsatisfied constraint ([ConstraintSchedulingExecutor.retrySchedule]), until at some point *all* of the constraints are
 * satisfied *at once*.
 *
 * This ultimately ends up with [ContextConstraint.schedule] being called one by one for every constraint of the chain that needs to be
 * scheduled. Finally, the runnable is called, executing the task in the properly arranged context.
 *
 * @author eldar
 * @author peter
 */
abstract class BaseConstrainedExecution<E : ConstrainedExecution<E>>(protected val constraints: Array<ContextConstraint>)
  : ConstrainedExecutionEx<E> {
  protected abstract fun cloneWith(constraints: Array<ContextConstraint>): E

  override fun withConstraint(constraint: ContextConstraint): E = cloneWith(constraints + constraint)

  fun asTaskExecutor() = ConstrainedTaskExecutor(this)
  fun asCoroutineDispatcher(): ContinuationInterceptor = ConstrainedCoroutineSupport(this).continuationInterceptor

  override fun createConstraintSchedulingExecutor(condition: BooleanSupplier?): Executor =
    when (condition) {
      null -> ConstraintSchedulingExecutor(constraints)
      else -> ConditionalConstraintSchedulingExecutor(constraints, condition)
    }

  open class ConstraintSchedulingExecutor(private val constraints: Array<ContextConstraint>) : Executor {
    override fun execute(runnable: Runnable) {
      for (constraint in constraints) {
        if (!constraint.isCorrectContext()) {
          return constraint.schedule(Runnable {
            LOG.assertTrue(constraint.isCorrectContext())
            retrySchedule(runnable, causeConstraint = constraint)
          })
        }
      }
      runnable.run()
    }

    protected open fun retrySchedule(runnable: Runnable, causeConstraint: ContextConstraint) {
      execute(runnable)
    }
  }

  open class ConditionalConstraintSchedulingExecutor(constraints: Array<ContextConstraint>,
                                                     private val condition: BooleanSupplier?) : ConstraintSchedulingExecutor(constraints) {
    override fun execute(runnable: Runnable) {
      if (condition?.asBoolean != false) {
        super.execute(runnable)
      }
    }
  }

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.constraints.ConstrainedExecution")
  }
}