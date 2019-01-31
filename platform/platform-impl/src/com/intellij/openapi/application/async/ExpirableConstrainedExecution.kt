// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.async.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.async.ExpirableConstrainedExecution.ExpirableConstraintDispatcher
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * This class adds support for cancelling the task on disposal of [Disposable]s associated using [expireWith] and other builder methods.
 * This also ensures that if the task is a coroutine suspended at some execution point, it's resumed with a [CancellationException] giving
 * the coroutine a chance to clean up any resources it might have acquired before suspending.
 *
 *
 * ## Implementation notes: ##
 *
 * Note: please, read the docs for [BaseConstrainedExecution] first.
 *
 * This subclass of AsyncExecutionSupport adds a notion of [Expiration] - something that might expire at some point, and can cause
 * a coroutine to be cancelled.
 *
 * A [Job] in the Kotlin Coroutines world is a basic way to interact with a coroutine. In fact, a coroutine _is_ a job, yet this is just
 * an implementation detail, and one usually retrieves the coroutine job from the [CoroutineContext]. A job can have parent and/or children,
 * and it has well-defined life-cycle w.r.t. parent-child relations, which includes error handling and job [cancellation][Job.cancel].
 *
 * So, the [Expiration] class is capable of cancelling jobs whenever something is expired. In our case, the "something" is either
 * a Disposable (see [DisposableExpiration]), or another job (see [JobExpiration]). Whenever a new Disposable needs to be tracked for its
 * expiration (added through [expireWith] or certain builder methods), a new ExpirableHandle is created and added to a set
 * of [handles][expirationSet].
 *
 * Upon [preparing][coroutineDispatchingContext] the execution context, the [expirationSet], if any, are [composed][composeExpiration]
 * into a single JobExpiration, and a hooked [ContinuationInterceptor] attaches that JobExpiration to every coroutine that runs with that
 * ContinuationInterceptor. That is, before running real code of every newly launched coroutine, it first registers that coroutine with
 * the JobExpiration, so that the coroutine is cancelled once the JobExpiration is expired. It is possible that the job is cancelled before
 * even getting to the real code, if the JobExpiration has already expired (due to expiration of any of the [expirationSet]).
 *
 * Another useful thing provided by this class is [ExpirableConstraintDispatcher] which is capable of dispatching
 * [ContextConstraint], which in turn represents an asynchronous execution service that might refuse to run a submitted task due to
 * disposal of an associated Disposable. For example, the DumbService used in [AppUIExecutorEx.inSmartMode] doesn't run any task once the
 * project is closed. The ExpirableConstraintDispatcher workarounds that limitation ensuring the task/continuation eventually runs even if
 * the corresponding disposable is expired.
 *
 * @author eldar
 */
internal abstract class ExpirableConstrainedExecution<E : ConstrainedExecution<E>>(dispatchers: Array<CoroutineDispatcher>,
                                                                                   private val expirationSet: Set<Expiration>)
  : BaseConstrainedExecution<E>(dispatchers) {

  /** Must schedule the runnable and return immediately. */
  protected abstract fun dispatchLater(block: Runnable)

  override fun createContinuationInterceptor(): ContinuationInterceptor {
    val delegateInterceptor = super.createContinuationInterceptor()
    val expiration = Expiration.composeExpiration(expirationSet) ?: return delegateInterceptor

    /* The [expiration] ultimately aims to be a proxy between a set of disposables that come from
     * [expireWith] and [withConstraint], and each individual coroutine job that must be cancelled
     * once any of the disposables is expired. */

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expiration.cancelJobOnExpiration(continuation.context[Job]!!)
        return delegateInterceptor.interceptContinuation(continuation)
      }

      override fun toString(): String = delegateInterceptor.toString()
    }
  }

  protected abstract fun cloneWith(dispatchers: Array<CoroutineDispatcher>, expirationSet: Set<Expiration>): E
  override fun cloneWith(dispatchers: Array<CoroutineDispatcher>): E = cloneWith(dispatchers, expirationSet)

  override fun withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    return cloneWith(dispatchers + ExpirableConstraintDispatcher(constraint, expirableHandle),
                     expirationSet + expirableHandle)
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    @Suppress("UNCHECKED_CAST")
    return if (expirableHandle in expirationSet) this as E else cloneWith(dispatchers, expirationSet + expirableHandle)
  }

  internal inner class ExpirableConstraintDispatcher(constraint: ContextConstraint,
                                                     private val expiration: Expiration) : ConstraintDispatcher(constraint) {
    @Suppress("EXPERIMENTAL_OVERRIDE")
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
      !(expiration.isExpired || constraint.isCorrectContext)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      val runOnce = RunOnce()

      val expirationHandle = expiration.invokeOnExpiration(Runnable {
        runOnce {
          if (!context[Job]!!.isCancelled) { // TODO[eldar] relying on the order of invocations of CompletionHandlers
            LOG.warn("Must have already been cancelled through the expirableHandle")
            context.cancel()
          }
          // Implementation of a completion handler must be fast and lock-free.
          dispatchLater(block)  // invokeLater, basically
        }
      })
      if (runOnce.isActive) {
        constraint.schedule(Runnable {
          runOnce {
            expirationHandle.unregisterHandler()
            block.run()
          }
        })
      }
    }
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
