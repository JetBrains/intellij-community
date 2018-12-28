// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.AsyncExecution.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.*
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.*
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
internal abstract class AsyncExecutionSupport<E : AsyncExecution<E>>(private val dispatcher: CoroutineDispatcher,
                                                                     private val expirableHandles: Set<JobExpiration>) : AsyncExecution<E> {

  private val myCoroutineDispatchingContext: CoroutineContext by lazy {
    val exceptionHandler = CoroutineExceptionHandler { context, throwable -> dispatcher.processUncaughtException(context, throwable) }
    val delegateDispatcherChain = generateSequence(dispatcher as? DelegateDispatcher) { it.delegate as? DelegateDispatcher }
    val coroutineName = CoroutineName("${javaClass.simpleName}(${delegateDispatcherChain.asIterable().reversed().joinToString("::")})")
    exceptionHandler + coroutineName + createExpirableJobContinuationInterceptor()
  }

  private fun createExpirableJobContinuationInterceptor(): ContinuationInterceptor {
    val delegateInterceptor = RescheduleAttemptLimitAwareDispatcher(dispatcher)
    val jobExpiration = composeJobExpiration()

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        jobExpiration.cancelJobOnExpiration(continuation.context[Job]!!)
        return delegateInterceptor.interceptContinuation(continuation)
      }
    }
  }

  private fun composeJobExpiration(): JobExpiration {
    val job = SupervisorJob()
    expirableHandles.forEach {
      it.cancelJobOnExpiration(job)
    }
    return SimpleJobExpiration(job)
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
  internal class ExpirableHandle(val disposable: Disposable) : JobExpiration() {
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

  override fun coroutineDispatchingContext(): CoroutineContext = myCoroutineDispatchingContext

  protected abstract fun cloneWith(dispatcher: CoroutineDispatcher, expirableHandles: Set<JobExpiration>): E

  override fun withConstraint(constraint: SimpleContextConstraint): E =
    cloneWith(SimpleConstraintDispatcher(dispatcher, constraint), expirableHandles)

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

  /** A CoroutineDispatcher which dispatches after ensuring its delegate is dispatched. */
  internal abstract class DelegateDispatcher(val delegate: CoroutineDispatcher) : CoroutineDispatcher()

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
        constraint.scheduleExpirable(expirableHandle.disposable, Runnable {
          runOnce {
            jobDisposableHandle.dispose()
            retryDispatchRunnable.run()
          }
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
        processUncaughtException(context, TooManyRescheduleAttemptsException(myLastDispatchers))
        context.cancel()

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

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AsyncExecutionSupport")

    internal class RunOnce : (() -> Unit) -> Unit {
      val isActive get() = hasNotRunYet.get()  // inherently race-prone
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }
    }

    private val CoroutineDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = (this as? DelegateDispatcher)?.chainFallbackDispatcher ?: this

    private val DelegateDispatcher.chainFallbackDispatcher: CoroutineDispatcher
      get() = delegate.chainFallbackDispatcher

    internal inline fun CoroutineDispatcher.dispatchIfNeededOrInvoke(context: CoroutineContext,
                                                                     crossinline block: () -> Unit) {
      @Suppress("EXPERIMENTAL_API_USAGE")
      if (isDispatchNeeded(context)) {
        dispatch(context, Runnable { block() })
      }
      else {
        block()
      }
    }

    internal fun CoroutineDispatcher.fallbackDispatch(context: CoroutineContext, block: Runnable) {
      val primaryDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
      val fallbackDispatcher = primaryDispatcher ?: chainFallbackDispatcher

      // Invoke later unconditionally to avoid running arbitrary code from inside a completion handler.
      fallbackDispatcher.dispatch(context, block)
    }

    internal fun CoroutineDispatcher.processUncaughtException(context: CoroutineContext, throwable: Throwable) {
      try {
        PluginManager.processException(throwable)  // throws AssertionError in unit testing mode
      }
      catch (e: Throwable) {
        // rethrow on EDT outside the Coroutines machinery
        fallbackDispatch(context, Runnable { throw e })
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
      val function = { referencedJob: Job ->
        runOnce {
          referencedJob.cancel()
        }
      }
      val child =
        if (!weaklyReferencedJob) Disposable { function(job) }
        else WeaklyReferencedDisposable(job, function)

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
        val jobCompletionUnregisteringHandle = job.invokeOnCompletion(completionHandler)
        return AutoCloseable {
          jobCompletionUnregisteringHandle.dispose()
          completionHandler(null)
        }
      }
    }

    internal class WeaklyReferencedDisposable<T : Any>(referent: T,
                                                       private val block: (T) -> Unit) : WeakReference<T>(referent, myRefQueue),
                                                                                         Disposable {
      override fun dispose() {
        val referent = get() ?: return
        clear()
        block(referent)
      }

      companion object {
        private val myRefQueue = ReferenceQueue<Any>()
          get() = field.apply {
            reapCollectedRefs()
          }

        private fun ReferenceQueue<Any>.reapCollectedRefs() {
          while (true) {
            val ref = poll() as? WeaklyReferencedDisposable ?: return
            Disposer.dispose(ref)
          }
        }
      }
    }
  }
}
