// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.AsyncExecution.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.experimental.*
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
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
internal abstract class AsyncExecutionSupport<E : AsyncExecution<E>> : AsyncExecution<E> {

  protected abstract val disposables: Set<Disposable>
  protected abstract val dispatcher: CoroutineDispatcher

  /**
   * An expirable job serves as a proxy between the set of disposables and each individual coroutine job
   * that must be cancelled once any of the disposables is expired.
   * The expirable job does not have children or a parent. Unlike the regular parent-children job relation,
   * having coroutine jobs attached to the expirable job doesn't imply waiting of any kind, neither does
   * coroutine cancellation affect the expirable job state.
   *
   * Introducing the expirable job primarily has performance considerations
   * (single lock-free Job.invokeOnCompletion vs. multiple synchronized Disposer.register calls per each launched coroutine)
   * and simplicity (using homogeneous Job API to setup coroutine cancellation).
   */
  private val myExpirableJob = Job()  // initialized once in createExpirableJobContinuationInterceptor()
  private val myCoroutineDispatchingContext: CoroutineContext by lazy {
    val exceptionHandler = CoroutineExceptionHandler(::handleUncaughtException)
    val delegateDispatcherChain = generateSequence(dispatcher as? DelegateDispatcher) { it.delegate as? DelegateDispatcher }
    val coroutineName = CoroutineName("${javaClass.simpleName}(${delegateDispatcherChain.asIterable().reversed().joinToString("::")})")
    exceptionHandler + coroutineName + createExpirableJobContinuationInterceptor()
  }

  private fun createExpirableJobContinuationInterceptor(): ContinuationInterceptor {
    val expirableJob = myExpirableJob
    val wrappedDispatcher = RescheduleAttemptLimitAwareDispatcher(dispatcher)

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expirableJob.cancelJobOnCompletion(continuation.context[Job]!!)
        return wrappedDispatcher.interceptContinuation(continuation)
      }
    }.also { interceptor ->
      initializeExpirableJob(expirableJob, disposables, interceptor)
    }
  }

  override fun coroutineDispatchingContext(): CoroutineContext = myCoroutineDispatchingContext

  override fun shutdown(cause: Throwable?) {
    myExpirableJob.cancel(cause)
  }

  protected abstract fun cloneWith(disposables: Set<Disposable>, dispatcher: CoroutineDispatcher): E

  override fun withConstraint(constraint: ContextConstraint): E {
    val disposables = if (constraint is ExpirableContextConstraint) disposables + constraint.expirable else disposables
    return cloneWith(disposables, constraint.toCoroutineDispatcher(dispatcher))
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val disposables = disposables + parentDisposable
    @Suppress("UNCHECKED_CAST")
    return if (disposables == this.disposables) this as E else cloneWith(disposables, dispatcher)
  }

  // MUST NOT throw. See https://github.com/Kotlin/kotlinx.coroutines/issues/562
  // #562 "Exceptions thrown by CoroutineExceptionHandler must be caught by handleCoroutineException()"
  protected open fun handleUncaughtException(coroutineContext: CoroutineContext, throwable: Throwable) {
    try {
      LOG.error("Uncaught exception from $coroutineContext", throwable)  // throws AssertionError in unit testing mode
    }
    catch (e: Throwable) {
      // rethrow on EDT outside the Coroutines machinery
      dispatcher.fallbackDispatch(coroutineContext, Runnable { throw e })
    }
  }

  /** A CoroutineDispatcher which dispatches after ensuring its delegate is dispatched. */
  internal abstract class DelegateDispatcher(val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext) = true  // because of the need to check the delegate
  }

  /** A DelegateDispatcher backed by a ContextConstraint. */
  internal abstract class ChainedDispatcher(delegate: CoroutineDispatcher) : DelegateDispatcher(delegate) {
    // This optimization eliminates the need to recurse through each link of the chain
    // down to the outermost delegate dispatcher and back, which is quite hard to debug usually.
    private val myChain: Array<ChainedDispatcher> = run {
      val delegateChain = (delegate as? ChainedDispatcher)?.myChain ?: emptyArray()
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
        if (dispatcher.isScheduleNeeded(context)) {
          return dispatcher.doSchedule(context, Runnable {
            LOG.assertTrue(!dispatcher.isScheduleNeeded(context), this)
            this.retryDispatch(context, block, causeDispatcher = dispatcher)
          })
        }
      }
      block.run()
    }

    protected open fun retryDispatch(context: CoroutineContext, block: Runnable,
                                     causeDispatcher: ChainedDispatcher) = this.dispatch(context, block)

    protected abstract fun isScheduleNeeded(context: CoroutineContext): Boolean
    protected abstract fun doSchedule(context: CoroutineContext, retryDispatchRunnable: Runnable)
  }

  /** A DelegateDispatcher backed by a ContextConstraint. */
  internal abstract class ChainedConstraintDispatcher(delegate: CoroutineDispatcher,
                                                      protected open val constraint: ContextConstraint) : ChainedDispatcher(delegate) {
    override fun isScheduleNeeded(context: CoroutineContext): Boolean = !constraint.isCorrectContext
    override fun toString() = constraint.toString()
  }

  /** @see SimpleContextConstraint */
  internal class SimpleConstraintDispatcher(delegate: CoroutineDispatcher,
                                            constraint: SimpleContextConstraint) : ChainedConstraintDispatcher(delegate, constraint) {
    override fun doSchedule(context: CoroutineContext, retryDispatchRunnable: Runnable) {
      (constraint as SimpleContextConstraint).schedule(retryDispatchRunnable)
    }
  }

  /** @see ExpirableContextConstraint */
  internal class ExpirableConstraintDispatcher(delegate: CoroutineDispatcher,
                                               constraint: ExpirableContextConstraint) : ChainedConstraintDispatcher(delegate, constraint) {
    override val constraint get() = super.constraint as ExpirableContextConstraint
    private val expirable: Disposable get() = constraint.expirable

    override fun isScheduleNeeded(context: CoroutineContext): Boolean = !(expirable.isDisposed || constraint.isCorrectContext)

    override fun doSchedule(context: CoroutineContext, retryDispatchRunnable: Runnable) {
      val runOnce = RunOnce()

      val jobDisposableCloseable = expirable.registerOrInvokeDisposable {
        runOnce {
          LOG.assertTrue(expirable.isDisposing || expirable.isDisposed, "Must only be called on disposal of $expirable")
          context.cancel(DisposedException(expirable))
          // Although it is tempting to invoke the retryDispatchRunnable directly,
          // it is better to avoid executing arbitrary code from inside the disposal handler.
          fallbackDispatch(context, retryDispatchRunnable)  // invokeLater, basically
        }
      }
      if (runOnce.isActive) {
        constraint.scheduleExpirable(runOnce.runnable {
          jobDisposableCloseable.close()
          retryDispatchRunnable.run()
        })
      }
    }
  }

  // must be the ContinuationInterceptor in order to work properly
  private class RescheduleAttemptLimitAwareDispatcher(delegate: CoroutineDispatcher,
                                                      private val myLimit: Int = 3000) : ChainedDispatcher(delegate) {
    private var myAttemptCount: Int = 0

    private val myLogLimit: Int = 30
    private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

    override fun isScheduleNeeded(context: CoroutineContext): Boolean = false
    override fun doSchedule(context: CoroutineContext,
                            retryDispatchRunnable: Runnable) = throw UnsupportedOperationException()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      resetAttemptCount()
      super.dispatch(context, block)
    }

    override fun retryDispatch(context: CoroutineContext,
                               block: Runnable,
                               causeDispatcher: ChainedDispatcher) {
      if (checkHaveMoreRescheduleAttempts(causeDispatcher)) {
        super.dispatch(context, block)
      }
      else {
        context.cancel(TooManyRescheduleAttemptsException(myLastDispatchers))

        // The continuation block MUST be invoked at some point in order to give the coroutine a chance
        // to handle the cancellation exception and exit gracefully.
        // At this point we can only provide a guarantee to resume it on EDT with a proper modality state.
        fallbackDispatch(context, block)
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
    : Exception("Too many reschedule requests, probably constraints can't be satisfied all together: " + lastConstraints.joinToString())

  class DisposedException(disposable: Disposable) : CancellationException("Already disposed: $disposable")

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AppUIExecutorImpl")

    internal class RunOnce : (() -> Unit) -> Unit {
      val isActive get() = hasNotRunYet.get()  // inherently race-prone
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }

      fun runnable(block: () -> Unit) = Runnable { block() }
    }

    private val CoroutineDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = (this as? DelegateDispatcher)?.chainFallbackDispatcher ?: this

    private val DelegateDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = delegate.chainFallbackDispatcher

    internal inline fun CoroutineDispatcher.dispatchIfNeededOrInvoke(context: CoroutineContext,
                                                                     crossinline block: () -> Unit) {
      if (isDispatchNeeded(context)) {
        dispatch(context, Runnable { block() })
      }
      else {
        block()
      }
    }

    internal fun CoroutineDispatcher.fallbackDispatch(context: CoroutineContext, block: Runnable) {
      val primaryDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
      val fallbackDispatcher = primaryDispatcher?.chainFallbackDispatcher ?: this.chainFallbackDispatcher

      //      fallbackDispatcher.dispatchIfNeededOrInvoke(context, block)
      if (fallbackDispatcher !== Dispatchers.Unconfined) {
        // Invoke later unconditionally to avoid running arbitrary code from inside dispose() handler.
        fallbackDispatcher.dispatch(context, block)
      }
      else {
        block.run()
      }
    }

    internal val Disposable.isDisposed: Boolean
      get() = Disposer.isDisposed(this)
    internal val Disposable.isDisposing: Boolean
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

    internal fun Disposable.registerOrInvokeDisposable(job: Job? = null,
                                                       disposableBlock: () -> Unit): AutoCloseable {
      val runOnce = RunOnce()
      val child = Disposable {
        runOnce {
          disposableBlock()
        }
      }
      if (!tryRegisterDisposable(this, child)) {
        Disposer.dispose(child)  // runs disposableBlock()
        return AutoCloseable { }
      }
      else {
        val completionHandler: CompletionHandler = {
          runOnce {
            Disposer.dispose(child)  // unregisters only, does not run disposableBlock()
          }
        }
        val jobCompletionUnregisteringHandle = job?.invokeOnCompletion(completionHandler)
        return AutoCloseable {
          jobCompletionUnregisteringHandle?.dispose()
          completionHandler(null)
        }
      }
    }

    internal fun Disposable.cancelJobOnDisposal(job: Job): AutoCloseable {
      val debugTraceThrowable = Throwable()
      return registerOrInvokeDisposable(job) {
        if (!job.isCancelled && !job.isCompleted) {
          job.cancel(DisposedException(this).apply {
            addSuppressed(debugTraceThrowable)
          })
        }
      }
    }

    internal fun Job.cancelJobOnCompletion(job: Job) {
      invokeOnCompletion { cause ->
        job.cancel(cause)
      }.also { handle ->
        job.disposeOnCompletion(handle)
      }
    }

    internal fun initializeExpirableJob(job: Job, disposables: Collection<Disposable>, referent: Any) {
      if (disposables.isNotEmpty() && job.isActive) {
        // Technically, this creates a leak through the Disposer tree...
        disposables.forEach { disposable ->
          disposable.cancelJobOnDisposal(job)
        }
        // ... which is mitigated by creating a PhantomReference to the interceptor instance,
        // which unregisters the job from the tree once the corresponding instance is GC'ed.
        val reference = RunnableReference(referent) {
          job.cancel()  // unregister from the Disposer tree
        }
        job.invokeOnCompletion { reference.clear() }  // essentially to hold a hard ref to the Reference instance
      }

      RunnableReference.reapCollectedRefs()
    }

    private class RunnableReference(referent: Any, private val finalizer: () -> Unit) : PhantomReference<Any>(referent, myRefQueue) {
      companion object {
        private val myRefQueue = ReferenceQueue<Any>()

        fun reapCollectedRefs() {
          while (true) {
            val ref = myRefQueue.poll() as? RunnableReference ?: return
            ref.finalizer()
          }
        }
      }
    }
  }
}
