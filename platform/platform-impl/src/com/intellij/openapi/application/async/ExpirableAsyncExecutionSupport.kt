// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.WeaklyReferencedDisposable
import com.intellij.openapi.application.async.AsyncExecution.ExpirableContextConstraint
import com.intellij.openapi.application.async.ExpirableAsyncExecutionSupport.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
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
 * Note: please, read the docs for [BaseAsyncExecutionSupport] first.
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
 * [ExpirableContextConstraint], which in turn represents an asynchronous execution service that might refuse to run a submitted task due to
 * disposal of an associated Disposable. For example, the DumbService used in [AppUIExecutorEx.inSmartMode] doesn't run any task once the
 * project is closed. The ExpirableConstraintDispatcher workarounds that limitation ensuring the task/continuation eventually runs even if
 * the corresponding disposable is expired.
 *
 * @author eldar
 */
internal abstract class ExpirableAsyncExecutionSupport<E : AsyncExecution<E>>(dispatchers: Array<CoroutineDispatcher>,
                                                                              private val expirationSet: Set<Expiration>)
  : BaseAsyncExecutionSupport<E>(dispatchers) {

  /** Must schedule the runnable and return immediately. */
  protected abstract fun dispatchLater(block: Runnable)

  override fun createContinuationInterceptor(): ContinuationInterceptor {
    val delegateInterceptor = super.createContinuationInterceptor()
    val expiration = composeExpiration() ?: return delegateInterceptor

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expiration.cancelJobOnExpiration(continuation.context[Job]!!)
        return delegateInterceptor.interceptContinuation(continuation)
      }

      override fun toString(): String = delegateInterceptor.toString()
    }
  }

  private fun composeExpiration(): Expiration? =
    when (expirationSet.size) {
      0 -> null
      1 -> expirationSet.single()
      else -> {
        val job = SupervisorJob()
        expirationSet.forEach {
          it.cancelJobOnExpiration(job)
        }
        JobExpiration(job)
      }
    }

  /**
   * Capable of invoking a handler whenever something expires -
   * either a Disposable (see [DisposableExpiration]), or another job (see [JobExpiration]).
   */
  internal abstract class Expiration {
    protected abstract val job: Job

    open val isExpired: Boolean
      get() = job.isCancelled

    /** The caller must ensure the returned handle is properly disposed. */
    fun invokeOnExpiration(handler: CompletionHandler): DisposableHandle =
      job.invokeOnCompletion(handler)
  }

  internal class JobExpiration(override val job: Job) : Expiration()

  /**
   * An ExpirableHandle isolates interactions with a Disposable and the Disposer, using
   * an expirable supervisor job that gets cancelled whenever the Disposable is disposed.
   *
   * A supervisor Job is easier to interact with because of using homogeneous Job API to setup
   * coroutine cancellation, and w.r.t. its lifecycle and memory management. Using it also has
   * performance considerations: two lock-free Job.invokeOnCompletion calls vs. multiple
   * synchronized Disposer calls per each launched coroutine.
   *
   * The ExpirableHandle itself is a lightweight thing w.r.t. creating it until it's supervisor Job
   * is really used, because registering a child Disposable within the Disposer tree happens lazily.
   *
   * The whole thing ultimately aims to be a proxy between a set of disposables that come from
   * [expireWith] and [withConstraint], and each individual coroutine job that must be cancelled
   * once any of the disposables is expired.
   */
  internal class DisposableExpiration(private val disposable: Disposable) : Expiration() {
    /**
     * Does not have children or a parent. Unlike the regular parent-children job relation,
     * having coroutine jobs attached to the supervisor job doesn't imply waiting of any kind,
     * neither does coroutine cancellation affect the supervisor job state.
     */
    override val job by lazy {
      SupervisorJob().also { job ->
        disposable.cancelJobOnDisposal(job, weaklyReferencedJob = true)  // the job doesn't leak through Disposer
      }
    }

    override val isExpired: Boolean
      get() = job.isCancelled && disposable.isDisposed

    override fun equals(other: Any?): Boolean = other is DisposableExpiration && disposable === other.disposable
    override fun hashCode(): Int = System.identityHashCode(disposable)
  }

  protected abstract fun cloneWith(dispatchers: Array<CoroutineDispatcher>, expirationSet: Set<Expiration>): E
  override fun cloneWith(dispatchers: Array<CoroutineDispatcher>): E = cloneWith(dispatchers, expirationSet)

  override fun withConstraint(constraint: ExpirableContextConstraint, parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    return cloneWith(dispatchers + ExpirableConstraintDispatcher(constraint, expirableHandle),
                     expirationSet + expirableHandle)
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    @Suppress("UNCHECKED_CAST")
    return if (expirableHandle in expirationSet) this as E else cloneWith(dispatchers, expirationSet + expirableHandle)
  }

  /** @see ExpirableContextConstraint */
  internal inner class ExpirableConstraintDispatcher(constraint: ExpirableContextConstraint,
                                                     private val expiration: Expiration) : ConstraintDispatcher(constraint) {
    override val constraint get() = super.constraint as ExpirableContextConstraint

    @Suppress("EXPERIMENTAL_OVERRIDE")
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
      !(expiration.isExpired || constraint.isCorrectContext)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      val runOnce = RunOnce()

      val jobDisposableHandle = expiration.invokeOnExpiration {
        runOnce {
          if (!context[Job]!!.isCancelled) { // TODO[eldar] relying on the order of invocations of CompletionHandlers
            LOG.warn("Must have already been cancelled through the expirableHandle")
            context.cancel()
          }
          // Implementation of a completion handler must be fast and lock-free.
          dispatchLater(block)  // invokeLater, basically
        }
      }
      if (runOnce.isActive) {
        constraint.scheduleExpirable(Runnable {
          runOnce {
            jobDisposableHandle.dispose()
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

    internal fun Expiration.cancelJobOnExpiration(job: Job) {
      invokeOnExpiration {
        job.cancel()
      }.also { handle ->
        job.invokeOnCompletion { handle.dispose() }
      }
    }

    internal val Disposable.isDisposed: Boolean
      get() = Disposer.isDisposed(this)
    private val Disposable.isDisposing: Boolean
      get() = Disposer.isDisposing(this)

    private fun tryRegisterDisposable(parent: Disposable, child: Disposable): Boolean {
      if (!parent.isDisposing &&
          !parent.isDisposed) {
        try {
          Disposer.register(parent, child)
          return true
        }
        catch (ignore: IncorrectOperationException) {  // Sorry but Disposer.register() is inherently thread-unsafe
        }
      }
      return false
    }

    /**
     * NOTE: there may be a hard ref to the [job] in the returned handle.
     */
    internal fun Disposable.cancelJobOnDisposal(job: Job,
                                                weaklyReferencedJob: Boolean = false): AutoCloseable {
      val runOnce = RunOnce()
      val child = Disposable {
        runOnce {
          job.cancel()
        }
      }
      val childRef =
        if (!weaklyReferencedJob) child
        else WeaklyReferencedDisposable(child)

      if (!tryRegisterDisposable(this, childRef)) {
        Disposer.dispose(childRef)  // runs disposableBlock()
        return AutoCloseable { }
      }
      else {
        val completionHandler = object : CompletionHandler {
          @Suppress("unused")
          val hardRefToChild = child  // transitive: job -> completionHandler -> child

          override fun invoke(cause: Throwable?) {
            runOnce {
              Disposer.dispose(childRef)  // unregisters only, does not run disposableBlock()
            }
          }
        }
        val jobCompletionUnregisteringHandle = job.invokeOnCompletion(completionHandler)
        return AutoCloseable {
          jobCompletionUnregisteringHandle.dispose()
          completionHandler(null)
        }
      }
    }
  }
}
