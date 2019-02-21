// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution
import com.intellij.openapi.application.constraints.Expiration
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable

/**
 * @author peter
 * @author eldar
 */
internal class AppUIExecutorImpl private constructor(private val modality: ModalityState,
                                                     constraints: Array<ContextConstraint>,
                                                     expirableHandles: Set<Expiration>)
  : ExpirableConstrainedExecution<AppUIExecutorEx>(constraints, expirableHandles), AppUIExecutorEx {

  constructor(modality: ModalityState) : this(modality, arrayOf(/* fallback */ object : ContextConstraint {
    override fun isCorrectContext(): Boolean =
      ApplicationManager.getApplication().isDispatchThread && !ModalityState.current().dominates(modality)

    override fun schedule(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, modality)
    }

    override fun toString() = "onUiThread($modality)"
  }), emptySet<Expiration>())

  override fun cloneWith(constraints: Array<ContextConstraint>, expirationSet: Set<Expiration>): AppUIExecutorEx =
    AppUIExecutorImpl(modality, constraints, expirationSet)

  override fun dispatchLaterUnconstrained(runnable: Runnable) =
    ApplicationManager.getApplication().invokeLater(runnable, modality)

  override fun execute(command: Runnable): Unit = asExecutor().execute(command)
  override fun submit(task: Runnable): CancellablePromise<*> = asExecutor().submit(task)
  override fun <T : Any?> submit(task: Callable<T>): CancellablePromise<T> = asExecutor().submit(task)

  override fun later(): AppUIExecutor {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else -1
    return withConstraint(object : ContextConstraint {
      @Volatile
      var usedOnce: Boolean = false

      override fun isCorrectContext(): Boolean =
        when (edtEventCount) {
          -1 -> ApplicationManager.getApplication().isDispatchThread
          else -> usedOnce || edtEventCount != IdeEventQueue.getInstance().eventCount
        }

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().invokeLater({
                                                          usedOnce = true
                                                          runnable.run()
                                                        }, modality)
      }

      override fun toString() = "later"
    })
  }

  override fun withDocumentsCommitted(project: Project): AppUIExecutor {
    return withConstraint(WithDocumentsCommitted(project, modality), project)
  }

  override fun inSmartMode(project: Project): AppUIExecutor {
    return withConstraint(InSmartMode(project), project)
  }

  override fun inTransaction(parentDisposable: Disposable): AppUIExecutor {
    val id = TransactionGuard.getInstance().contextTransaction
    return withConstraint(object : ContextConstraint {
      override fun isCorrectContext(): Boolean =
        TransactionGuard.getInstance().contextTransaction != null

      override fun schedule(runnable: Runnable) {
        // The Application instance is passed as a disposable here to ensure the runnable is always invoked,
        // regardless expiration state of the proper parentDisposable. In case the latter is disposed,
        // a continuation is resumed with a cancellation exception anyway (.expireWith() takes care of that).
        TransactionGuard.getInstance().submitTransaction(ApplicationManager.getApplication(), id, runnable)
      }

      override fun toString() = "inTransaction"
    }).expireWith(parentDisposable)
  }

  override fun inUndoTransparentAction(): AppUIExecutor {
    return withConstraint(object : ContextConstraint {
      override fun isCorrectContext(): Boolean =
        CommandProcessor.getInstance().isUndoTransparentActionInProgress

      override fun schedule(runnable: Runnable) {
        CommandProcessor.getInstance().runUndoTransparentAction(runnable)
      }

      override fun toString() = "inUndoTransparentAction"
    })
  }

  override fun inWriteAction(): AppUIExecutor {
    return withConstraint(object : ContextConstraint {
      override fun isCorrectContext(): Boolean =
        ApplicationManager.getApplication().isWriteAccessAllowed

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable)
      }

      override fun toString() = "inWriteAction"
    })
  }
}

internal class WithDocumentsCommitted(private val project: Project, private val modality: ModalityState) : ContextConstraint {
  override fun isCorrectContext(): Boolean =
    !PsiDocumentManager.getInstance(project).hasUncommitedDocuments()

  override fun schedule(runnable: Runnable) {
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, modality)
  }

  override fun toString() = "withDocumentsCommitted"
}

internal class InSmartMode(private val project: Project) : ContextConstraint {
  override fun isCorrectContext(): Boolean =
    !DumbService.getInstance(project).isDumb

  override fun schedule(runnable: Runnable) {
    DumbService.getInstance(project).runWhenSmart(runnable)
  }

  override fun toString() = "inSmartMode"
}