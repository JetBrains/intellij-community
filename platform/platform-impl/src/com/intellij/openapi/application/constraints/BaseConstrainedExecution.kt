// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.map2Array
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import java.util.function.BooleanSupplier

/**
 * This class is responsible for running a task in a proper context defined using various builder methods of this class and it's
 * implementations, like [com.intellij.openapi.application.AppUIExecutor.later], or generic [withConstraint].
 *
 * ## Implementation notes: ##
 *
 * The [scheduleWithinConstraints] starts checking the list of constraints, one by one, rescheduling and restarting itself
 * for each unsatisfied constraint until at some point *all* of the constraints are satisfied *at once*.
 *
 * This ultimately ends up with [ContextConstraint.schedule] being called one by one for every constraint that needs to be scheduled.
 * Finally, the runnable is called, executing the task in the properly arranged context.
 *
 * @author eldar
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
    scheduleWithinConstraints(runnable, condition, constraints)

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.application.constraints.ConstrainedExecution")

    @JvmStatic
    @ApiStatus.Internal
    fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier?, constraints: Array<ContextConstraint>) {
      val attemptChain = mutableListOf<ContextConstraint>()

      fun inner() {
        if (attemptChain.size > 3000) {
          val lastCauses = attemptChain.takeLast(15)
          LOG.error("Too many reschedule requests, probably constraints can't be satisfied all together",
                    *lastCauses.map2Array { it.toString() })
        }

        if (condition?.asBoolean == false) return
        for (constraint in constraints) {
          if (!constraint.isCorrectContext()) {
            return constraint.schedule(Runnable {
              if (!constraint.isCorrectContext()) {
                LOG.error("ContextConstraint scheduled into incorrect context: $constraint",
                          *constraints.map2Array { it.toString() })
              }
              attemptChain.add(constraint)
              inner()
            })
          }
        }
        runnable.run()
      }

      inner()
    }

  }
}