// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.experimental.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * @author eldar
 */
internal abstract class AsyncExecutionSupport {
  protected abstract val disposables: Set<Disposable>
  protected abstract val constraints: Array<out ContextConstraint>

  protected open val fallbackConstraint: ContextConstraint
    get() = constraints[0]

  open suspend fun <T> runCoroutine(block: suspend () -> T): T {
    val job = Job(coroutineContext[Job])

    if (disposables.isNotEmpty()) {
      val debugTraceThrowable = Throwable()
      for (parent in disposables) {
        val child = Disposable {
          if (!job.isCancelled && !job.isCompleted) {
            job.cancel(DisposedException(parent).apply {
              addSuppressed(debugTraceThrowable)
            })
          }
        }
        Disposer.register(parent, child)
        job.invokeOnCompletion {
          Disposer.dispose(child, false)
        }
      }
    }

    val newContext = newCoroutineContext(coroutineContext, job)
    val dispatcher = createCoroutineDispatcher(newContext)

    return withContext(newContext + dispatcher) {
      block()
    }
  }

  protected open fun createCoroutineDispatcher(context: CoroutineContext): CoroutineDispatcher =
    CompositeCoroutineDispatcherWithRescheduleAttemptLimit(constraints, fallbackConstraint)

  protected abstract class ContextConstraint : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext) = !isCorrectContext

    abstract val isCorrectContext: Boolean
    abstract override fun toString(): String
  }

  /**
   * Implementation MUST guarantee to execute a runnable passed to [schedule] at some point.
   * For dispatchers that may refuse to run the task based on some condition
   * consider using [ExpirableContextConstraint] instead.
   */
  protected abstract class SimpleContextConstraint : ContextConstraint() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      schedule(Runnable {
        LOG.assertTrue(isCorrectContext, this)
        block.run()
      })
    }

    abstract fun schedule(runnable: Runnable)
  }

  /**
   * This class ensures that a coroutine continuation is invoked at some point
   * even if the underlying dispatcher doesn't usually run a task once some [Disposable] is disposed.
   *
   * At the very least, the implementation MUST guarantee to execute a runnable passed to [scheduleExpirable]
   * if the corresponding [expirable] is not disposed by the time the dispatcher arranges the proper execution context.
   * It is OK to execute it if the [expirable] has been disposed though.
   */
  protected abstract class ExpirableContextConstraint(val expirable: Disposable,
                                                      private val myFallbackDispatcher: CoroutineDispatcher) : ContextConstraint() {
    private val isExpired get() = Disposer.isDisposing(expirable) || Disposer.isDisposed(expirable)

    override fun isDispatchNeeded(context: CoroutineContext) = super.isDispatchNeeded(context) && !isExpired

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      val runOnce = RunOnce()

      val job = context[Job]!!
      val childDisposable = invokeWhenExpired {
        runOnce {
          // defer in case this disposable cleans up before the one which cancels the job in runCoroutine()
          job.invokeOnCompletion(onCancelling = true) {
            myFallbackDispatcher.dispatch(context, Runnable {
              LOG.assertTrue(job.isCancelled, "The job should have been cancelled by a disposable registered in runCoroutine()")
              block.run()
            })
          }
        }
      }

      scheduleExpirable(Runnable {
        runOnce {
          Disposer.dispose(childDisposable, false)  // doesn't run disposal code; just unregisters the disposable
          block.run()
        }
      })
    }

    private fun invokeWhenExpired(block: () -> Unit) =
      Disposable { block() }.also { childDisposable ->
        fun tryRegister(): Boolean =
          try {
            Disposer.register(expirable, childDisposable)
            true
          }
          catch (e: IncorrectOperationException) {  // Sorry but Disposer.register() is inherently thread-unsafe
            false
          }
        if (isExpired || !tryRegister()) {
          childDisposable.dispose()
        }
      }

    abstract fun scheduleExpirable(runnable: Runnable)
  }

  protected abstract class CompositeCoroutineDispatcher : CoroutineDispatcher() {
    protected abstract val dispatchers: Array<out CoroutineDispatcher>

    override fun isDispatchNeeded(context: CoroutineContext) = true  // we're gonna check it in dispatch() anyway
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      for (dispatcher in dispatchers) {
        if (delegateDispatchIfNeeded(dispatcher, context, block)) {
          return
        }
      }
      block.run()
    }

    protected open fun delegateDispatchIfNeeded(dispatcher: CoroutineDispatcher,
                                                context: CoroutineContext,
                                                block: Runnable): Boolean {
      if (dispatcher.isDispatchNeeded(context)) {
        delegateDispatch(dispatcher, context, block)
        return true
      }
      return false
    }

    protected open fun delegateDispatch(dispatcher: CoroutineDispatcher,
                                        context: CoroutineContext,
                                        block: Runnable) {
      dispatcher.dispatch(context, Runnable {
        this.dispatch(context, block)  // retry
      })
    }

    override fun toString() = dispatchers.joinToString()
  }

  protected class CompositeCoroutineDispatcherWithRescheduleAttemptLimit(override val dispatchers: Array<out CoroutineDispatcher>,
                                                                         private val myFallbackDispatcher: CoroutineDispatcher,
                                                                         private val myLimit: Int = 3000) : CompositeCoroutineDispatcher() {
    private var myAttemptCount: Int = 0

    private val myLogLimit: Int = 30
    private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

    override fun delegateDispatchIfNeeded(dispatcher: CoroutineDispatcher, context: CoroutineContext, block: Runnable): Boolean {
      return super.delegateDispatchIfNeeded(dispatcher, context, block).also { isDispatchNeeded ->
        if (!isDispatchNeeded) {
          myLastDispatchers.clear()
          myAttemptCount = 0
        }
      }
    }

    override fun delegateDispatch(dispatcher: CoroutineDispatcher, context: CoroutineContext, block: Runnable) {
      if (checkHaveMoreRescheduleAttempts(dispatcher)) {
        super.delegateDispatch(dispatcher, context, block)
      }
      else {
        context.cancel(TooManyRescheduleAttemptsException(myLastDispatchers))  // makes block.run() call resumeWithException()

        // The continuation block MUST be invoked at some point in order to give the coroutine a chance
        // to handle the cancellation exception and exit gracefully.
        // At this point we can only provide a guarantee to resume it on EDT with a proper modality state.
        myFallbackDispatcher.dispatch(context, block)
      }
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
    }

    private operator fun <T> Set<T>.plus(element: T): Set<T> = if (element in this) this else this.plusElement(element)
  }
}
