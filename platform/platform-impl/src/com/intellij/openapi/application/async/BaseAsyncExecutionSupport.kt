// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.async.AsyncExecution.ContextConstraint
import com.intellij.openapi.application.async.AsyncExecution.SimpleContextConstraint
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Runnable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * @author eldar
 */
abstract class BaseAsyncExecutionSupport<E : AsyncExecution<E>>(protected val dispatcher: CoroutineDispatcher) : AsyncExecution<E> {
  private val myCoroutineDispatchingContext: CoroutineContext by lazy {
    val exceptionHandler = CoroutineExceptionHandler { context, throwable -> dispatcher.processUncaughtException(context, throwable) }
    val delegateDispatcherChain = generateSequence(dispatcher as? DelegateDispatcher) { it.delegate as? DelegateDispatcher }
    val coroutineName = CoroutineName("${javaClass.simpleName}(${delegateDispatcherChain.asIterable().reversed().joinToString("::")})")
    exceptionHandler + coroutineName + createContinuationInterceptor()
  }

  protected open fun createContinuationInterceptor(): ContinuationInterceptor = RescheduleAttemptLimitAwareDispatcher(dispatcher)

  override fun coroutineDispatchingContext(): CoroutineContext = myCoroutineDispatchingContext

  protected abstract fun cloneWith(dispatcher: CoroutineDispatcher): E

  override fun withConstraint(constraint: SimpleContextConstraint): E =
    cloneWith(SimpleConstraintDispatcher(dispatcher, constraint))

  /** A CoroutineDispatcher which dispatches after ensuring its delegate is dispatched. */
  internal abstract class DelegateDispatcher(val delegate: CoroutineDispatcher) : CoroutineDispatcher()

  internal abstract class ChainedDispatcher(delegate: CoroutineDispatcher) : DelegateDispatcher(delegate) {
    // This optimization eliminates the need to recurse through each link of the chain
    // down to the outermost delegate dispatcher and back, which is quite hard to debug usually.
    private val myChain: Array<ChainedDispatcher> = run {
      val delegateChain = (delegate as? ChainedDispatcher)?.myChain ?: emptyArray()
      arrayOf(*delegateChain, this)
    }

    private val myChainDelegate: CoroutineDispatcher = myChain[0].delegate  // outside the chain, the same as findChainFallbackDispatcher()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      myChainDelegate.dispatchIfNeededOrInvoke(context) {
        dispatchChain(context, block)
      }
    }

    private fun dispatchChain(context: CoroutineContext, block: Runnable) {
      for (dispatcher in myChain) {
        if (dispatcher.isScheduleNeeded(context)) {
          return dispatcher.doSchedule(context, retryDispatchRunnable = Runnable {
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

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AsyncExecutionSupport")
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

    /**
     * Chain fallback is the first [CoroutineDispatcher] defined, which then gets wrapped by a chain of [DelegateDispatcher]s.
     * This function unwraps the chain following the [DelegateDispatcher.delegate] property.
     * In other words, this returns the very first dispatcher which is checked in [ChainedDispatcher.dispatch]
     * (which performs, for example, `ApplicationManager.getApplication().invokeLater(block, modality)`).
     */
    private fun CoroutineDispatcher.findChainFallbackDispatcher(): CoroutineDispatcher {
      var dispatcher = this
      while (dispatcher is DelegateDispatcher) {
        dispatcher = dispatcher.delegate
      }
      return dispatcher
    }

    internal fun CoroutineDispatcher.fallbackDispatch(context: CoroutineContext, block: Runnable) {
      // Invoke later unconditionally to avoid running arbitrary code from inside a completion handler.
      findChainFallbackDispatcher().dispatch(context, block)
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
  }
}