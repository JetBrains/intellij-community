// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.experimental.Runnable

/**
 * @author peter
 * @author eldar
 */
internal class AppUIExecutorImpl private constructor(private val myModality: ModalityState,
                                                     override val disposables: Set<Disposable>,
                                                     override vararg val constraints: ContextConstraint) : AsyncExecutionSupport(),
                                                                                                           AppUIExecutorEx {

  constructor(modality: ModalityState) : this(modality, emptySet<Disposable>(), /* fallback */ object : SimpleContextConstraint() {
    override val isCorrectContext: Boolean
      get() = ApplicationManager.getApplication().isDispatchThread && !ModalityState.current().dominates(modality)

    override fun schedule(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, modality)
    }

    override fun toString() = "onUiThread($modality)"
  })

  private fun withConstraint(element: ContextConstraint): AppUIExecutor {
    val disposables = (element as? ExpirableContextConstraint)?.expirable?.let { disposable ->
      disposables + disposable
    } ?: disposables
    return AppUIExecutorImpl(myModality, disposables, *constraints, element)
  }

  override fun later(): AppUIExecutor {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else null
    return withConstraint(object : SimpleContextConstraint() {
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
    return withConstraint(object : ExpirableContextConstraint(project, fallbackConstraint) {
      override val isCorrectContext: Boolean
        get() = !PsiDocumentManager.getInstance(project).hasUncommitedDocuments()

      override fun scheduleExpirable(runnable: Runnable) {
        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, myModality)
      }

      override fun toString() = "withDocumentsCommitted"
    })
  }

  override fun inSmartMode(project: Project): AppUIExecutor {
    return withConstraint(object : ExpirableContextConstraint(project, fallbackConstraint) {
      override val isCorrectContext: Boolean
        get() = !DumbService.getInstance(project).isDumb

      override fun scheduleExpirable(runnable: Runnable) {
        DumbService.getInstance(project).smartInvokeLater(runnable, myModality)
      }

      override fun toString() = "inSmartMode"
    })
  }

  override fun inTransaction(parentDisposable: Disposable): AppUIExecutor {
    val id = TransactionGuard.getInstance().contextTransaction
    return withConstraint(object : ExpirableContextConstraint(parentDisposable, fallbackConstraint) {
      override val isCorrectContext: Boolean
        get() = TransactionGuard.getInstance().contextTransaction != null

      override fun scheduleExpirable(runnable: Runnable) {
        TransactionGuard.getInstance().submitTransaction(parentDisposable, id, runnable)
      }

      override fun toString() = "inTransaction"
    })
  }

  override fun inUndoTransparentAction(): AppUIExecutor {
    return withConstraint(object : SimpleContextConstraint() {
      override val isCorrectContext: Boolean
        get() = CommandProcessor.getInstance().isUndoTransparentActionInProgress

      override fun schedule(runnable: Runnable) {
        CommandProcessor.getInstance().runUndoTransparentAction(runnable)
      }

      override fun toString() = "inUndoTransparentAction"
    })
  }

  override fun inWriteAction(): AppUIExecutor {
    return withConstraint(object : SimpleContextConstraint() {
      override val isCorrectContext: Boolean
        get() = ApplicationManager.getApplication().isWriteAccessAllowed

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable)
      }

      override fun toString() = "inWriteAction"
    })
  }

  override fun expireWith(parentDisposable: Disposable): AppUIExecutor {
    val disposables = disposables + parentDisposable
    return if (disposables === this.disposables) this else AppUIExecutorImpl(myModality, disposables, *constraints)
  }
}
