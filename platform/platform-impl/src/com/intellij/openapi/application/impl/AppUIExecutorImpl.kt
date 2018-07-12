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
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.experimental.*
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import java.util.*
import java.util.concurrent.Callable

/**
 * @author peter
 */
internal class AppUIExecutorImpl private constructor(private val myModality: ModalityState,
                                                     private val myDisposables: Set<Disposable>,
                                                     private vararg val myConstraints: ConstrainedExecutor) : AppUIExecutor {

  constructor(modality: ModalityState) : this(modality, emptySet<Disposable>(), object : ConstrainedExecutor() {
    override val isCorrectContext: Boolean
      get() = ApplicationManager.getApplication().isDispatchThread && !ModalityState.current().dominates(modality)

    override fun doReschedule(runnable: Runnable) {
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

      override fun doReschedule(runnable: Runnable) {
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

      override fun doReschedule(runnable: Runnable) {
        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, myModality)
      }

      override fun toString() = "withDocumentsCommitted"
    }).expireWith(project)
  }

  override fun inSmartMode(project: Project): AppUIExecutor {
    return withConstraint(object : ConstrainedExecutor() {
      override val isCorrectContext: Boolean
        get() = !DumbService.getInstance(project).isDumb

      override fun doReschedule(runnable: Runnable) {
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

      override fun doReschedule(runnable: Runnable) {
        TransactionGuard.getInstance().submitTransaction(parentDisposable, id, runnable)
      }

      override fun toString() = "inTransaction"
    }).expireWith(parentDisposable)
  }

  override fun expireWith(parentDisposable: Disposable): AppUIExecutor {
    if (myDisposables.contains(parentDisposable)) return this

    val disposables = ContainerUtil.newHashSet(myDisposables)
    disposables.add(parentDisposable)
    return AppUIExecutorImpl(myModality, disposables, *myConstraints)
  }

  override fun submit(task: Runnable): CancellablePromise<*> {
    return submit<Any> {
      task.run()
      null
    }
  }

  override fun execute(command: Runnable) {
    submit(command)
  }

  override fun <T> submit(task: Callable<T>): CancellablePromise<T> {
    val promise = AsyncPromise<T>()

    if (!myDisposables.isEmpty()) {
      val children = ArrayList<Disposable>()
      for (parent in myDisposables) {
        val child = Disposable { promise.cancel() }
        children.add(child)
        Disposer.register(parent, child)
      }
      promise.onProcessed { children.forEach(Consumer<Disposable> { Disposer.dispose(it) }) }
    }

    checkConstraints(task, promise, ArrayList())
    return promise
  }

  private fun <T> checkConstraints(task: Callable<T>, future: AsyncPromise<T>, log: MutableList<ConstrainedExecutor>) {
    val app = ApplicationManager.getApplication()
    if (!app.isDispatchThread) {
      app.invokeLater({ checkConstraints(task, future, log) }, myModality)
      return
    }

    if (future.isCancelled) return

    for (constraint in myConstraints) {
      if (!constraint.isCorrectContext) {
        log.add(constraint)
        if (log.size > 3000) {
          LOG.error(
            "Too many reschedule requests, probably constraints can't be satisfied all together: " + log.subList(log.size - 30, log.size))
        }
        else {
          constraint.rescheduleInCorrectContext(Runnable { checkConstraints(task, future, log) })
        }
        return
      }
    }

    try {
      val result = task.call()
      future.setResult(result)
    }
    catch (e: Throwable) {
      future.setError(e)
    }

  }

  private abstract class ConstrainedExecutor {
    abstract val isCorrectContext: Boolean
    abstract fun doReschedule(runnable: Runnable)
    abstract override fun toString(): String

    internal fun rescheduleInCorrectContext(r: Runnable) {
      doReschedule(Runnable {
        LOG.assertTrue(isCorrectContext, this)
        r.run()
      })
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.application.impl.AppUIExecutorImpl")
  }
}
