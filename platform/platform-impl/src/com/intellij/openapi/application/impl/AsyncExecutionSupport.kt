// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.AsyncExecution.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashSet
import kotlinx.coroutines.experimental.*
import java.lang.UnsupportedOperationException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.ContinuationInterceptor
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * @author eldar
 */
internal abstract class AsyncExecutionSupport<E : AsyncExecution<E>> : AsyncExecution<E> {

  protected abstract val disposables: Set<Disposable>
  protected abstract val dispatcher: CoroutineDispatcher

  protected abstract fun cloneWith(disposables: Set<Disposable>, dispatcher: CoroutineDispatcher): E

  override fun withConstraint(constraint: ContextConstraint): E {
    return cloneWith(disposables, constraint.toCoroutineDispatcher(dispatcher))
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val disposables = disposables + parentDisposable
    @Suppress("UNCHECKED_CAST")
    return if (disposables == this.disposables) this as E else cloneWith(disposables, dispatcher)
  }

  /**
   * Creates a new [CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
   *
   * The context inherits from the specified [one][context], and contains a child [Job] [initialized][createChildJob]
   * so that it is cancelled whenever any of the [disposables] is expired, and a custom [dispatcher] for the installed context constraints
   * (possibly wrapped using the [createCoroutineDispatcher] method), which takes care to establish the necessary execution context.
   */
  override fun createJobContext(context: CoroutineContext, parent: Job?): CoroutineContext {
    val dispatcher = createCoroutineDispatcher()
    val delegateChain = generateSequence(dispatcher as? DelegateDispatcher) { it.delegate as? DelegateDispatcher }.toList().asReversed()

    val job = createChildJob(parent ?: context[Job], delegateChain)

    return newCoroutineContext(context, job) + dispatcher
  }

  protected open fun createChildJob(parent: Job?, dispatchers: List<DelegateDispatcher>): Job =
    Job(parent).also { job ->
      disposables.forEach { disposable ->
        disposable.cancelJobOnDisposal(job)
      }
      dispatchers.forEach { dispatcher ->
        dispatcher.initializeJob(job)
      }
    }

  protected open fun createCoroutineDispatcher(): DelegateDispatcher =
    RescheduleAttemptLimitAwareDispatcher(dispatcher)

  /** A CoroutineDispatcher which dispatches after ensuring its delegate is dispatched. */
  internal abstract class DelegateDispatcher(val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    abstract val isChainFallback: Boolean

    open fun initializeJob(job: Job) = Unit

    override fun isDispatchNeeded(context: CoroutineContext) = true  // because of the need to check the delegate
  }

  /** A DelegateDispatcher backed by a ContextConstraint. */
  internal abstract class ChainedConstraintDispatcher(delegate: CoroutineDispatcher) : DelegateDispatcher(delegate),
                                                                                       ContextConstraint {
    override val isChainFallback: Boolean
      get() = false  // TODO[eldar] any ContextConstraint-backed dispatcher is considered unreliable to be a chain fallback

    // This optimization eliminates the need to recurse through each link of the chain
    // down to the outermost delegate dispatcher and back, which is quite hard to debug usually.
    private val myChain: Array<ChainedConstraintDispatcher> = run {
      val delegateChain = (delegate as? ChainedConstraintDispatcher)?.myChain ?: emptyArray()
      arrayOf(*delegateChain, this)
    }

    private val myChainDelegate: CoroutineDispatcher = myChain[0].delegate  // outside the chain

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      myChainDelegate.dispatchIfNeededOrInvoke(context) {
        dispatchChain(context, block)
      }
    }

    private fun dispatchChain(context: CoroutineContext, block: Runnable) {
      for (dispatcher in myChain) {
        if (!dispatcher.isCorrectContext) {
          dispatcher.doConstraintSchedule(context) { isRetryDispatchNeeded ->
            if (isRetryDispatchNeeded) {
              LOG.assertTrue(dispatcher.isCorrectContext, this)
              this.retryDispatch(context, block, causeDispatcher = dispatcher)
            }
            else {
              block.run()
            }
          }
          return
        }
      }
      block.run()
    }

    protected open fun retryDispatch(context: CoroutineContext,
                                     block: Runnable,
                                     causeDispatcher: ChainedConstraintDispatcher) = this.dispatch(context, block)

    protected abstract fun doConstraintSchedule(context: CoroutineContext,
                                                block: (isRetryDispatchNeeded: Boolean) -> Unit)
  }

  /** @see SimpleContextConstraint */
  internal class SimpleConstraintDispatcher(delegate: CoroutineDispatcher,
                                            private val constraint: SimpleContextConstraint) : ChainedConstraintDispatcher(delegate),
                                                                                               SimpleContextConstraint by constraint {
    override fun doConstraintSchedule(context: CoroutineContext,
                                      block: (isRetryDispatchNeeded: Boolean) -> Unit) {
      schedule(Runnable {
        block(true)
      })
    }
    override fun toString() = constraint.toString()
  }

  /** @see ExpirableContextConstraint */
  internal class ExpirableConstraintDispatcher(delegate: CoroutineDispatcher,
                                               private val constraint: ExpirableContextConstraint) : ChainedConstraintDispatcher(delegate),
                                                                                                     ExpirableContextConstraint by constraint {
    private val myInvokeOnDisposal = object : THashSet<Runnable>(ContainerUtil.identityStrategy()) {
      var isDisposed: Boolean = false
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) {
          field = value
        }

      fun disposeAndGet(): Set<Runnable> = synchronized(this) {
        isDisposed = true
        ContainerUtil.newIdentityTroveSet<Runnable>(this).also {
          this.clear()
        }
      }

      fun register(runnable: Runnable): Boolean = synchronized(this) {
        !isDisposed && this.add(runnable)
      }

      fun unregister(runnable: Runnable): Boolean = synchronized(this) {
        this.remove(runnable)
      }
    }

    override fun initializeJob(job: Job) {
      super.initializeJob(job)
      expirable.cancelJobOnDisposal(job) {
        myInvokeOnDisposal.disposeAndGet().forEach(Runnable::run)
      }
    }

    override val isCorrectContext: Boolean
      get() = !myInvokeOnDisposal.isDisposed && constraint.isCorrectContext

    override fun doConstraintSchedule(context: CoroutineContext,
                                      block: (isRetryDispatchNeeded: Boolean) -> Unit) {
      val runOnce = RunOnce()

      val fallbackRunnable = runOnce.runnable {
        LOG.assertTrue(context[Job]!!.isCancelled, "Job should have been cancelled through initializeJob()")
        fallbackDispatch(context) { block(false) }
      }
      if (!myInvokeOnDisposal.register(fallbackRunnable)) {
        fallbackRunnable.run()
      }
      else {
        scheduleExpirable(runOnce.runnable {
          myInvokeOnDisposal.unregister(fallbackRunnable)
          block(true)
        })
      }
    }

    override fun toString() = "$constraint : $expirable"
  }

  // must be the ContinuationInterceptor in order to work properly
  private class RescheduleAttemptLimitAwareDispatcher(delegate: CoroutineDispatcher,
                                                      private val myLimit: Int = 3000) : ChainedConstraintDispatcher(delegate) {
    private var myAttemptCount: Int = 0

    private val myLogLimit: Int = 30
    private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

    override val isCorrectContext get() = true
    override fun doConstraintSchedule(context: CoroutineContext,
                                      block: (isRetryDispatchNeeded: Boolean) -> Unit) = throw UnsupportedOperationException()
    override fun toCoroutineDispatcher(delegate: CoroutineDispatcher) = RescheduleAttemptLimitAwareDispatcher(delegate)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      resetAttemptCount()
      super.dispatch(context, block)
    }

    override fun retryDispatch(context: CoroutineContext,
                               block: Runnable,
                               causeDispatcher: ChainedConstraintDispatcher) {
      if (checkHaveMoreRescheduleAttempts(causeDispatcher)) {
        super.dispatch(context, block)
      }
      else {
        context.cancel(TooManyRescheduleAttemptsException(myLastDispatchers))  // makes block.run() call resumeWithException()

        // The continuation block MUST be invoked at some point in order to give the coroutine a chance
        // to handle the cancellation exception and exit gracefully.
        // At this point we can only provide a guarantee to resume it on EDT with a proper modality state.
        fallbackDispatch(context) { block.run() }
      }
    }

    private fun resetAttemptCount() {
      myLastDispatchers.clear()
      myAttemptCount = 0
    }

    private fun checkHaveMoreRescheduleAttempts(dispatcher: CoroutineDispatcher): Boolean {
      with(myLastDispatchers) {
        if (isNotEmpty() && size >= myLogLimit) removeFirst()
        addLast(dispatcher)
      }
      return ++myAttemptCount < myLimit
    }
  }

  /**
   * Thrown at a cancellation point when the executor is unable to arrange the requested context after a reasonable number of attempts.
   *
   * WARNING: The exception thrown is handled in a fallback context as a last resort,
   *          The fallback context is EDT with a proper modality state, no other guarantee is made.
   */
  class TooManyRescheduleAttemptsException internal constructor(lastConstraints: Collection<CoroutineDispatcher>)
    : CancellationException("Too many reschedule requests, probably constraints can't be satisfied all together: " +
                            lastConstraints.joinToString())

  class DisposedException(disposable: Disposable)
    : CancellationException("Already disposed: $disposable")

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AppUIExecutorImpl")

    internal class RunOnce : (() -> Unit) -> Unit {
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }

      fun runnable(block: () -> Unit) = Runnable { block() }
    }

    private val CoroutineDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = (this as? DelegateDispatcher)?.chainFallbackDispatcher ?: this

    private val DelegateDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = if (isChainFallback) this else delegate.chainFallbackDispatcher

    internal inline fun CoroutineDispatcher.dispatchIfNeededOrInvoke(context: CoroutineContext,
                                                                     crossinline block: () -> Unit) {
      if (isDispatchNeeded(context)) {
        dispatch(context, Runnable { block() })
      }
      else {
        block()
      }
    }

    internal fun CoroutineDispatcher.fallbackDispatch(context: CoroutineContext, block: () -> Unit) {
      val primaryDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
      val fallbackDispatcher = primaryDispatcher?.chainFallbackDispatcher ?: this.chainFallbackDispatcher

      fallbackDispatcher.dispatchIfNeededOrInvoke(context, block)
    }

    private fun tryRegisterDisposable(parent: Disposable, child: Disposable): Boolean {
      if (!Disposer.isDisposing(parent) &&
          !Disposer.isDisposed(parent)) {
        try {
          Disposer.register(parent, child)
          return true
        }
        catch (ignore: IncorrectOperationException) {  // Sorry but Disposer.register() is inherently thread-unsafe
        }
      }
      return false
    }

    private fun Disposable.registerOrInvokeJobDisposable(job: Job, disposableBlock: () -> Unit): Disposable {
      val runOnce = RunOnce()
      val child = Disposable {
        runOnce {
          disposableBlock()
        }
      }
      if (!tryRegisterDisposable(this, child)) {
        Disposer.dispose(child)
      }
      else {
        job.invokeOnCompletion {
          runOnce {
            Disposer.dispose(child)  // unregisters only, does not run disposableBlock()
          }
        }
      }
      return child
    }

    internal fun Disposable.cancelJobOnDisposal(job: Job, onceCancelledBlock: () -> Unit = {}) {
      val debugTraceThrowable = Throwable()
      registerOrInvokeJobDisposable(job) {
        if (!job.isCancelled && !job.isCompleted) {
          job.cancel(DisposedException(this).apply {
            addSuppressed(debugTraceThrowable)
          })
        }
        onceCancelledBlock()
      }
    }
  }

}
