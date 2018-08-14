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
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.ContinuationInterceptor
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * @author eldar
 */
internal abstract class AsyncExecutionSupport : AbstractCoroutineContextElement(ContinuationInterceptor),
                                                ContinuationInterceptor,
                                                AsyncExecution {

  protected abstract val disposables: Set<Disposable>
  protected abstract val dispatcher: CoroutineDispatcher

  /**
   * Invoked by the Coroutines framework just before the very first bits of coroutine code are executed.
   * The interceptor [initializes][createChildJob] a child [Job] within the [CoroutineContext], so that it is
   * cancelled whenever any of the [disposables] is expired, and replaces itself with the [dispatcher]
   * (possibly wrapped using the [createCoroutineDispatcher] method) as the new [ContinuationInterceptor]
   * of this coroutine, which then takes care to establish the necessary execution context.
   */
  override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
    val dispatcher = createCoroutineDispatcher()
    val delegateChain = generateSequence(dispatcher as? DelegateDispatcher) { it.delegate as? DelegateDispatcher }.toList().asReversed()

    val oldContext = continuation.context
    val job = createChildJob(oldContext, delegateChain)

    val newContext = newCoroutineContext(oldContext, job) + dispatcher

    return dispatcher.interceptContinuation(object : Continuation<T> by continuation {
      override val context get() = newContext
    })
  }

  protected open fun createChildJob(context: CoroutineContext, dispatchers: List<DelegateDispatcher>): Job =
    Job(context[Job]).also { job ->
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
  internal abstract class DelegateDispatcher : CoroutineDispatcher() {
    abstract val delegate: CoroutineDispatcher
    abstract val isChainFallback: Boolean

    open fun initializeJob(job: Job) = Unit

    override fun isDispatchNeeded(context: CoroutineContext) = true  // because of the need to check the delegate

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      delegate.dispatchIfNeededOrInvoke(context) {
        doDispatch(context, block)
      }
    }

    protected abstract fun doDispatch(context: CoroutineContext, block: Runnable)
  }

  /** A DelegateDispatcher backed by a ContextConstraint. */
  internal abstract class ChainedConstraintDispatcher(private val myDelegate: CoroutineDispatcher) : DelegateDispatcher(),
                                                                                                     ContextConstraint {
    override val isChainFallback: Boolean
      get() = false  // TODO[eldar] any ContextConstraint-backed dispatcher is considered unreliable to be a chain fallback

    override val delegate: CoroutineDispatcher  // outside the chain
      get() = myDelegateChain[0].myDelegate

    // This optimization eliminates the need to recurse through each link of the chain
    // down to the outermost delegate dispatcher and back, which is quite hard to debug usually.
    private val myDelegateChain: Array<ChainedConstraintDispatcher> = run {
      val delegateChain = (myDelegate as? ChainedConstraintDispatcher)?.myDelegateChain ?: emptyArray()
      arrayOf(*delegateChain, this)
    }

    override fun doDispatch(context: CoroutineContext, block: Runnable) {
      for (dispatcher in myDelegateChain) {
        if (!dispatcher.isCorrectContext) {
          delegateSchedule(dispatcher, context, block)
          return
        }
      }
      block.run()
    }

    protected open fun delegateSchedule(dispatcher: ChainedConstraintDispatcher,
                                        context: CoroutineContext,
                                        block: Runnable) {
      dispatcher.doConstraintSchedule(context, Runnable {
        LOG.assertTrue(dispatcher.isCorrectContext, this)
        dispatch(context, block)  // retry
      })
    }

    protected abstract fun doConstraintSchedule(context: CoroutineContext, block: Runnable)
  }

  /** @see SimpleContextConstraint */
  internal class SimpleConstraintDispatcher(delegate: CoroutineDispatcher,
                                            constraint: SimpleContextConstraint) : ChainedConstraintDispatcher(delegate),
                                                                                   SimpleContextConstraint by constraint {
    override fun doConstraintSchedule(context: CoroutineContext, block: Runnable) = schedule(block)
    override fun toString() = super.toString()
  }

  /** @see ExpirableContextConstraint */
  internal class ExpirableConstraintDispatcher(delegate: CoroutineDispatcher,
                                               private val constraint: ExpirableContextConstraint) : ChainedConstraintDispatcher(delegate),
                                                                                                     ExpirableContextConstraint by constraint {
    private val myDisposable = object : THashSet<Runnable>(ContainerUtil.identityStrategy()) {
      var isDisposed: Boolean = false
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) {
          field = value
        }

      fun disposeAndClear(): Set<Runnable> = synchronized(this) {
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
        myDisposable.disposeAndClear().forEach(Runnable::run)
      }
    }

    override val isCorrectContext: Boolean
      get() = !myDisposable.isDisposed && constraint.isCorrectContext

    override fun doConstraintSchedule(context: CoroutineContext, block: Runnable) {
      val runOnce = RunOnce()

      val fallbackRunnable = runOnce.runnable {
        LOG.assertTrue(context[Job]!!.isCancelled, "Job should have been cancelled through initializeJob()")
        fallbackDispatch(context) { block.run() }
      }
      if (!myDisposable.register(fallbackRunnable)) {
        fallbackRunnable.run()
      }
      else {
        scheduleExpirable(runOnce.runnable {
          myDisposable.unregister(fallbackRunnable)
          block.run()
        })
      }
    }

    override fun toString() = "${expirable}@${super.toString()}"
  }

  private class RescheduleAttemptLimitAwareDispatcher(delegate: CoroutineDispatcher,
                                                      private val myLimit: Int = 3000) : ChainedConstraintDispatcher(delegate) {
    private var myAttemptCount: Int = 0

    private val myLogLimit: Int = 30
    private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

    override val isCorrectContext get() = true
    override fun doConstraintSchedule(context: CoroutineContext, block: Runnable) = block.run()  // never used
    override fun toCoroutineDispatcher(delegate: CoroutineDispatcher) = RescheduleAttemptLimitAwareDispatcher(delegate)

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
      resetAttemptCount()
      return super.interceptContinuation(continuation)
    }

    override fun delegateSchedule(dispatcher: ChainedConstraintDispatcher, context: CoroutineContext, block: Runnable) {
      if (checkHaveMoreRescheduleAttempts(dispatcher)) {
        dispatcher.dispatch(context, block)
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
    private val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AppUIExecutorImpl")

    private class RunOnce : (() -> Unit) -> Unit {
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }

      fun runnable(block: () -> Unit) = Runnable { block() }
    }

    private operator fun <T> Set<T>.plus(element: T): Set<T> = if (element in this) this else this.plusElement(element)

    private val CoroutineDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = (this as? DelegateDispatcher)?.chainFallbackDispatcher ?: this

    private val DelegateDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = if (isChainFallback) this else delegate.chainFallbackDispatcher

    private fun CoroutineDispatcher.dispatchIfNeededOrInvoke(context: CoroutineContext, block: () -> Unit) {
      if (isDispatchNeeded(context)) {
        dispatch(context, Runnable { block() })
      }
      else {
        block()
      }
    }

    private fun CoroutineDispatcher.fallbackDispatch(context: CoroutineContext, block: () -> Unit) {
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

    private fun Disposable.cancelJobOnDisposal(job: Job, onceCancelledBlock: () -> Unit = {}) {
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
