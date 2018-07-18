// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.experimental.*
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext

/**
 * @author peter
 * @author eldar
 */
internal class AppUIExecutorImpl private constructor(private val myModality: ModalityState,
                                                     private val myDisposables: Set<Disposable>,
                                                     private vararg val myConstraints: ConstrainedExecutor) : AppUIExecutorEx {

  constructor(modality: ModalityState) : this(modality, emptySet<Disposable>(), object : ConstrainedExecutor() {
    override val isCorrectContext: Boolean
      get() = ApplicationManager.getApplication().isDispatchThread && !ModalityState.current().dominates(modality)

    override fun schedule(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, modality)
    }

    override fun toString() = "onUiThread($modality)"
  })

  private fun withConstraint(element: ConstrainedExecutor): AppUIExecutor {
    return AppUIExecutorImpl(myModality, myDisposables, *myConstraints, element)
  }

  override fun later(): AppUIExecutor {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else null
    return withConstraint(object : ConstrainedExecutor() {
      @Volatile
      var usedOnce: Boolean = false

      override val isCorrectContext: Boolean
        get() = if (edtEventCount == null)
          ApplicationManager.getApplication().isDispatchThread
        else
          edtEventCount != IdeEventQueue.getInstance().eventCount || usedOnce

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().invokeLater({
                                                          usedOnce = true
                                                          runnable.run()
                                                        }, myModality)
      }

      override fun toString() = "later"
    })
  }

  override fun withDocumentsCommitted(project: Project): AppUIExecutor {
    return withConstraint(object : ConstrainedExecutor() {
      override val isCorrectContext: Boolean
        get() = !PsiDocumentManager.getInstance(project).hasUncommitedDocuments()

      override fun schedule(runnable: Runnable) {
        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, myModality)
      }

      override fun toString() = "withDocumentsCommitted"
    }).expireWith(project)
  }

  override fun inSmartMode(project: Project): AppUIExecutor {
    return withConstraint(object : ConstrainedExecutor() {
      override val isCorrectContext: Boolean
        get() = !DumbService.getInstance(project).isDumb

      override fun schedule(runnable: Runnable) {
        DumbService.getInstance(project).smartInvokeLater(runnable, myModality)
      }

      override fun toString() = "inSmartMode"
    }).expireWith(project)
  }

  override fun inTransaction(parentDisposable: Disposable): AppUIExecutor {
    val id = TransactionGuard.getInstance().contextTransaction
    return withConstraint(object : ConstrainedExecutor() {
      override val isCorrectContext: Boolean
        get() = TransactionGuard.getInstance().contextTransaction != null

      override fun schedule(runnable: Runnable) {
        TransactionGuard.getInstance().submitTransaction(parentDisposable, id, runnable)
      }

      override fun toString() = "inTransaction"
    }).expireWith(parentDisposable)
  }

  override fun expireWith(parentDisposable: Disposable): AppUIExecutor {
    val disposables = myDisposables + parentDisposable
    return if (disposables === myDisposables) this else AppUIExecutorImpl(myModality, disposables, *myConstraints)
  }

  override suspend fun <T> runCoroutine(block: suspend () -> T): T {
    val job = Job(coroutineContext[Job])

    if (myDisposables.isNotEmpty()) {
      val debugTraceThrowable = Throwable()
      for (parent in myDisposables) {
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
    val dispatcher = CompositeCoroutineDispatcherWithRescheduleAttemptLimit(myConstraints)

    return withContext(newContext + dispatcher) {
      block()
    }
  }

  private abstract class ConstrainedExecutor : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext) = !isCorrectContext
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      schedule(Runnable {
        LOG.assertTrue(isCorrectContext, this)
        block.run()
      })
    }

    abstract val isCorrectContext: Boolean
    abstract fun schedule(runnable: Runnable)
    abstract override fun toString(): String
  }

  private abstract class CompositeCoroutineDispatcher : CoroutineDispatcher() {
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

  private class CompositeCoroutineDispatcherWithRescheduleAttemptLimit(override val dispatchers: Array<out CoroutineDispatcher>,
                                                                       private val myLimit: Int = 3000) : CompositeCoroutineDispatcher() {
    private var myAttemptCount: Int = 0

    private val myLogLimit: Int = 30
    private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

    private val myFallbackDispatcher: CoroutineDispatcher
      get() = dispatchers[0] // EDT + ModalityState (refer to AppUIExecutorImpl constructor)

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

    private operator fun <T> Set<T>.plus(element: T): Set<T> = if (element in this) this else this.plusElement(element)
  }
}
