// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.WeaklyReferencedDisposable
import com.intellij.openapi.application.impl.AsyncExecution.ExpirableContextConstraint
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * @author eldar
 */
internal abstract class AsyncExecutionExpirableSupport<E : AsyncExecution<E>>(dispatcher: CoroutineDispatcher,
                                                                              private val expirableHandles: Set<JobExpiration>)
  : AsyncExecutionBaseSupport<E>(dispatcher) {

  override fun createContinuationInterceptor(): ContinuationInterceptor {
    val delegateInterceptor = super.createContinuationInterceptor()
    val jobExpiration = composeJobExpiration()

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        jobExpiration?.cancelJobOnExpiration(continuation.context[Job]!!)
        return delegateInterceptor.interceptContinuation(continuation)
      }
    }
  }

  private fun composeJobExpiration(): JobExpiration? =
    when (expirableHandles.size) {
      0 -> null
      1 -> expirableHandles.single()
      else -> {
        val job = SupervisorJob()
        expirableHandles.forEach {
          it.cancelJobOnExpiration(job)
        }
        SimpleJobExpiration(job)
      }
    }

  internal abstract class JobExpiration {
    protected abstract val job: Job

    /** The caller must ensure the returned handle is properly disposed. */
    fun invokeOnExpiration(handler: CompletionHandler): DisposableHandle =
      job.invokeOnCompletion(handler)

    fun cancelJobOnExpiration(job: Job) {
      invokeOnExpiration {
        job.cancel()
      }.also { handle ->
        job.invokeOnCompletion { handle.dispose() }
      }
    }
  }

  internal class SimpleJobExpiration(override val job: Job) : JobExpiration()

  /**
   * An ExpirableHandle isolates interactions with a Disposable and the Disposer, using
   * an expirable supervisor job that gets cancelled whenever the Disposable is disposed.
   *
   * A supervisor Job is easier to interact with because of using homogeneous Job API to setup
   * coroutine cancellation, and w.r.t. its lifecycle and memory management. Using it also has
   * performance considerations: two lock-free Job.invokeOnCompletion calls vs. multiple
   * synchronized Disposer calls per each launched coroutine.
   *
   * The whole thing ultimately aims to be a proxy between a set of disposables that come from
   * [expireWith] and [withConstraint], and each individual coroutine job that must be cancelled
   * once any of the disposables is expired.
   */
  internal class ExpirableHandle(private val disposable: Disposable) : JobExpiration() {
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
    val isExpired: Boolean
      get() = job.isCancelled && disposable.isDisposed

    override fun equals(other: Any?): Boolean = other is ExpirableHandle && disposable === other.disposable
    override fun hashCode(): Int = System.identityHashCode(disposable)
  }

  protected abstract fun cloneWith(dispatcher: CoroutineDispatcher, expirableHandles: Set<JobExpiration>): E
  override fun cloneWith(dispatcher: CoroutineDispatcher): E = cloneWith(dispatcher, expirableHandles)

  override fun withConstraint(constraint: ExpirableContextConstraint, parentDisposable: Disposable): E {
    val expirableHandle = ExpirableHandle(parentDisposable)
    return cloneWith(ExpirableConstraintDispatcher(dispatcher, constraint, expirableHandle),
                     expirableHandles + expirableHandle)
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val expirableHandle = ExpirableHandle(parentDisposable)
    @Suppress("UNCHECKED_CAST")
    return if (expirableHandle in expirableHandles) this as E else cloneWith(dispatcher, expirableHandles + expirableHandle)
  }

  /** @see ExpirableContextConstraint */
  internal class ExpirableConstraintDispatcher(delegate: CoroutineDispatcher,
                                               constraint: ExpirableContextConstraint,
                                               private val expirableHandle: ExpirableHandle) : ChainedConstraintDispatcher(delegate,
                                                                                                                           constraint) {
    override val constraint get() = super.constraint as ExpirableContextConstraint

    override fun isScheduleNeeded(context: CoroutineContext): Boolean =
      !(expirableHandle.isExpired || constraint.isCorrectContext)

    override fun doSchedule(context: CoroutineContext, retryDispatchRunnable: Runnable) {
      val runOnce = RunOnce()

      val jobDisposableHandle = expirableHandle.invokeOnExpiration {
        runOnce {
          if (!context[Job]!!.isCancelled) { // TODO[eldar] relying on the order of invocations of CompletionHandlers
            LOG.warn("Must have already been cancelled through the expirableHandle")
            context.cancel()
          }
          // Implementation of a completion handler must be fast and lock-free.
          fallbackDispatch(context, retryDispatchRunnable)  // invokeLater, basically
        }
      }
      if (runOnce.isActive) {
        constraint.scheduleExpirable(Runnable {
          runOnce {
            jobDisposableHandle.dispose()
            retryDispatchRunnable.run()
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
