// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.async.AsyncExecution.*
import com.intellij.openapi.application.async.BaseAsyncExecutionSupport.CompositeDispatcher
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
 * Coroutine dispatchers used by this class are arranged into a [chain][CompositeDispatcher], with each dispatcher of the chain
 * called to ensure it's context constraint considers the current context [correct][ContextConstraint.isCorrectContext]. Whenever there's
 * a constraint in the chain that isn't satisfied, its [CoroutineDispatcher.dispatch] method is called to reschedule another
 * attempt to traverse the chain of constraints.
 *
 * So, the [CompositeDispatcher.dispatch] starts checking the chain of constraints, one by one, rescheduling and restarting itself for each
 * unsatisfied constraint ([CompositeDispatcher.retryDispatch]), until at some point *all* of the constraints are satisfied *at once*.
 *
 * This ultimately ends up with either [SimpleContextConstraint.schedule] or [ExpirableContextConstraint.scheduleExpirable] being called
 * one by one for every constraint of the chain that needs to be scheduled. Finally, the continuation runnable is called, executing the
 * task, or resuming the coroutine in the properly arranged context.

 * @author eldar
 */
abstract class BaseAsyncExecutionSupport<E : AsyncExecution<E>>(protected val dispatchers: Array<CoroutineDispatcher>) : AsyncExecution<E> {
  private val myCoroutineDispatchingContext: CoroutineContext by lazy {
    val continuationInterceptor = createContinuationInterceptor()
    val coroutineName = CoroutineName("${javaClass.simpleName}($continuationInterceptor)")
    val exceptionHandler = CoroutineExceptionHandler { _, throwable -> processUncaughtException(throwable) }
    exceptionHandler + coroutineName + continuationInterceptor
  }

  protected open fun composeDispatchers(): CoroutineDispatcher = dispatchers.singleOrNull() ?: CompositeDispatcher(dispatchers)
  protected open fun createContinuationInterceptor(): ContinuationInterceptor = composeDispatchers()

  override fun coroutineDispatchingContext(): CoroutineContext = myCoroutineDispatchingContext

  protected abstract fun cloneWith(dispatchers: Array<CoroutineDispatcher>): E

  override fun withConstraint(constraint: SimpleContextConstraint): E =
    cloneWith(dispatchers + SimpleConstraintDispatcher(constraint))

  internal open class CompositeDispatcher(val dispatchers: Array<CoroutineDispatcher>) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      for (dispatcher in dispatchers) {
        @Suppress("EXPERIMENTAL_API_USAGE")
        if (dispatcher.isDispatchNeeded(context)) {
          return dispatcher.dispatch(context, Runnable {
            this.retryDispatch(context, block, causeDispatcher = dispatcher)
          })
        }
      }
      block.run()
    }

    protected open fun retryDispatch(context: CoroutineContext, block: Runnable,
                                     causeDispatcher: CoroutineDispatcher) = this.dispatch(context, block)

    override fun toString(): String = dispatchers.joinToString("::")
  }

  internal abstract class ConstraintDispatcher(protected open val constraint: ContextConstraint): CoroutineDispatcher() {
    @Suppress("EXPERIMENTAL_OVERRIDE")
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !constraint.isCorrectContext
    override fun toString() = constraint.toString()
  }

  /** @see SimpleContextConstraint */
  internal class SimpleConstraintDispatcher(constraint: SimpleContextConstraint) : ConstraintDispatcher(constraint) {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      (constraint as SimpleContextConstraint).schedule(block)
    }
  }

  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AsyncExecutionSupport")

    internal fun processUncaughtException(throwable: Throwable) {
      PluginManager.processException(throwable)
    }
  }
}