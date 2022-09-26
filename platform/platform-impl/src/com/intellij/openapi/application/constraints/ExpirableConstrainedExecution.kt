// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Runnable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * This class adds support for cancelling the task on disposal of [Disposable]s associated using [expireWith] and other builder methods.
 * This also ensures that if the task is a coroutine suspended at some execution point, it's resumed with a [CancellationException] giving
 * the coroutine a chance to clean up any resources it might have acquired before suspending.
 *
 * @author eldar
 */
internal abstract class ExpirableConstrainedExecution<E : ConstrainedExecution<E>>(
  constraints: Array<ContextConstraint>,
  private val cancellationConditions: Array<BooleanSupplier>,
  private val expirationSet: Set<Expiration>
) : BaseConstrainedExecution<E>(constraints) {

  protected abstract fun cloneWith(constraints: Array<ContextConstraint>,
                                   cancellationConditions: Array<BooleanSupplier>,
                                   expirationSet: Set<Expiration>): E

  override fun cloneWith(constraints: Array<ContextConstraint>): E = cloneWith(constraints, cancellationConditions, expirationSet)

  override fun withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    val expirableConstraint = ExpirableContextConstraint(constraint, expirableHandle)
    return cloneWith(constraints + expirableConstraint, cancellationConditions, expirationSet + expirableHandle)
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    if (expirableHandle in expirationSet) @Suppress("UNCHECKED_CAST") return this as E
    return cloneWith(constraints, cancellationConditions, expirationSet + expirableHandle)
  }

  override fun cancelIf(condition: BooleanSupplier): E {
    return cloneWith(constraints, cancellationConditions + condition, expirationSet)
  }

  /** Must schedule the runnable and return immediately. */
  abstract fun dispatchLaterUnconstrained(runnable: Runnable)

  private val compositeExpiration: Expiration? by lazy(PUBLICATION) {
    Expiration.composeExpiration(expirationSet)
  }

  override fun composeExpiration(): Expiration? = compositeExpiration

  override fun composeCancellationCondition(): BooleanSupplier? {
    val conditions = cancellationConditions
    return when (conditions.size) {
      0 -> null
      1 -> conditions.single()
      else -> BooleanSupplier { conditions.any { it.asBoolean } }
    }
  }

  /**
   * Wraps an expirable context constraint so that the [schedule] method guarantees to execute runnables, regardless the [expiration] state.
   *
   * This is used in combination with execution services that might refuse to run a submitted task due to disposal of an associated
   * Disposable. For example, the DumbService used in [com.intellij.openapi.application.AppUIExecutor.inSmartMode] doesn't run any task once
   * the project is closed. The [ExpirableContextConstraint] workarounds that limitation, ensuring that even if the corresponding disposable
   * is expired, the task runs eventually, which in turn is crucial for Kotlin Coroutines to work properly.
   */
  internal inner class ExpirableContextConstraint(private val constraint: ContextConstraint,
                                                  private val expiration: Expiration) : ContextConstraint {
    override fun isCorrectContext(): Boolean =
      expiration.isExpired || constraint.isCorrectContext()

    override fun schedule(runnable: Runnable) {
      val runOnce = RunOnce()

      val invokeWhenCompletelyExpiredRunnable = object : Runnable {
        override fun run() {
          if (expiration.isExpired) {
            runOnce {
              // At this point all the expiration handlers, including the one responsible for cancelling the coroutine job, have finished.
              runnable.run()
            }
          }
          else if (runOnce.isActive) dispatchLaterUnconstrained(this)
        }
      }
      val expirationHandle = expiration.invokeOnExpiration(invokeWhenCompletelyExpiredRunnable)
      if (runOnce.isActive) {
        constraint.schedule(Runnable {
          runOnce {
            expirationHandle.unregisterHandler()
            runnable.run()
          }
        })
      }
    }

    override fun toString(): String = constraint.toString()
  }

  companion object {
    internal class RunOnce : (() -> Unit) -> Unit {
      val isActive get() = hasNotRunYet.get()  // inherently race-prone
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }
    }
  }
}
