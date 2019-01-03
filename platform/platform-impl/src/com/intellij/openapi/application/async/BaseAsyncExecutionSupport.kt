// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.async.AsyncExecution.*
import com.intellij.openapi.application.async.BaseAsyncExecutionSupport.ChainedConstraintDispatcher
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Execution context constraints backed by Kotlin Coroutines.
 *
 * This class is responsible for running a task in a proper context defined using various builder methods of this class and it's
 * implementations, like [AppUIExecutorEx.later], or generic [withConstraint].
 *
 *
 * ## Implementation notes: ##
 *
 * The main entry point performing execution of a task is the [coroutineDispatchingContext] function. It returns a [CoroutineContext] -
 * an immutable set of [CoroutineContext.Element]s that define various aspects of how exactly a coroutine is executed. Those include:
 *
 * - [CoroutineExceptionHandler], which handles any uncaught exceptions thrown from the coroutines;
 *
 * - [CoroutineName], which is only needed to ease the debugging. Example name: "AppUIExecutor::onUiThread(modality)";
 *
 * - and [ContinuationInterceptor], also known as [CoroutineDispatcher] - the most important element, which arranges the proper execution
 *   context based on the [ContextConstraint]s.
 *
 * Whenever a new coroutine is launched (see [launch], [async] and [withContext]), the ContinuationInterceptor is retrieved from the context
 * and used to schedule a task for execution in a proper context. In order to do that the Kotlin Coroutines machinery calls the
 * [CoroutineDispatcher.dispatch] method passing a runnable that represents a [Continuation]. A continuation is just a part of a coroutine
 * between two suspension points, or the whole task in case of using plain old [AppUIExecutorEx.submit]/[AppUIExecutorEx.execute].
 * The Continuation class itself is irrelevant, since it is only used under the hood, and coroutine dispatchers only operate with a plain
 * runnable.
 *
 * Coroutine dispatchers used by this class are arranged into a [chain][ChainedConstraintDispatcher], with each dispatcher of the chain
 * called to ensure it's context constraint considers the current context [correct][ContextConstraint.isCorrectContext]. Whenever there's
 * a constraint in the chain that isn't satisfied, its [ChainedConstraintDispatcher.doSchedule] method is called to reschedule another
 * attempt to traverse the chain of constraints.
 *
 * So, first of all, the [ChainedConstraintDispatcher.dispatch] schedules itself onto the proper thread (EDT in case of
 * [AppUIExecutorImpl]), once on the thread it starts checking the chain of constraints ([ChainedConstraintDispatcher.dispatchChain]), one
 * by one, rescheduling and restarting itself for each unsatisfied constraint ([ChainedConstraintDispatcher.retryDispatch]), until at some
 * point *all* of the constraints are satisfied *at once*.
 *
 * This ultimately ends up with either [SimpleContextConstraint.schedule] or [ExpirableContextConstraint.scheduleExpirable] being called
 * one by one for every constraint of the chain that needs to be scheduled. Finally, the continuation runnable is called, executing the
 * task, or resuming the coroutine in the properly arranged context.

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