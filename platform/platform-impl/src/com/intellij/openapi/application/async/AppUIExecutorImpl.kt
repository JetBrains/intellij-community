// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.async.AsyncExecution.ExpirableContextConstraint
import com.intellij.openapi.application.async.AsyncExecution.SimpleContextConstraint
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * @author peter
 * @author eldar
 */
internal class AppUIExecutorImpl private constructor(private val modality: ModalityState,
                                                     dispatchers: Array<CoroutineDispatcher>,
                                                     expirableHandles: Set<Expiration>)
  : ExpirableAsyncExecutionSupport<AppUIExecutorEx>(dispatchers, expirableHandles), AppUIExecutorEx {

  override fun composeDispatchers() = dispatchers.singleOrNull() ?: RescheduleAttemptLimitAwareDispatcher(dispatchers, ::dispatchLater)

  override fun dispatchLater(block: Runnable) =
    ApplicationManager.getApplication().invokeLater(block, modality)

  constructor(modality: ModalityState) : this(modality, arrayOf(/* fallback */ SimpleConstraintDispatcher(object : SimpleContextConstraint {
    override val isCorrectContext: Boolean
      get() = ApplicationManager.getApplication().isDispatchThread && !ModalityState.current().dominates(modality)

    override fun schedule(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, modality)
    }

    override fun toString() = "onUiThread($modality)"
  })), emptySet<Expiration>())

  override fun cloneWith(dispatchers: Array<CoroutineDispatcher>, expirationSet: Set<Expiration>): AppUIExecutorEx =
    AppUIExecutorImpl(modality, dispatchers, expirationSet)

  override fun later(): AppUIExecutor {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else -1
    return withConstraint(object : SimpleContextConstraint {
      @Volatile
      var usedOnce = false

      override val isCorrectContext: Boolean
        get() {
          return when (edtEventCount) {
            -1 -> ApplicationManager.getApplication().isDispatchThread
            else -> usedOnce || edtEventCount != IdeEventQueue.getInstance().eventCount
          }
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
    return withConstraint(object : ExpirableContextConstraint {
      override val isCorrectContext: Boolean
        get() = !PsiDocumentManager.getInstance(project).hasUncommitedDocuments()

      override fun scheduleExpirable(runnable: Runnable) {
        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, modality)
      }

      override fun toString() = "withDocumentsCommitted"
    }, project)
  }

  override fun inSmartMode(project: Project): AppUIExecutor {
    return withConstraint(object : ExpirableContextConstraint {
      override val isCorrectContext: Boolean
        get() = !DumbService.getInstance(project).isDumb

      override fun scheduleExpirable(runnable: Runnable) {
        DumbService.getInstance(project).runWhenSmart(runnable)
      }

      override fun toString() = "inSmartMode"
    }, project)
  }

  override fun inTransaction(parentDisposable: Disposable): AppUIExecutor {
    val id = TransactionGuard.getInstance().contextTransaction
    return withConstraint(object : SimpleContextConstraint {
      override val isCorrectContext: Boolean
        get() = TransactionGuard.getInstance().contextTransaction != null

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
    return withConstraint(object : SimpleContextConstraint {
      override val isCorrectContext: Boolean
        get() = CommandProcessor.getInstance().isUndoTransparentActionInProgress

      override fun schedule(runnable: Runnable) {
        CommandProcessor.getInstance().runUndoTransparentAction(runnable)
      }

      override fun toString() = "inUndoTransparentAction"
    })
  }

  override fun inWriteAction(): AppUIExecutor {
    return withConstraint(object : SimpleContextConstraint {
      override val isCorrectContext: Boolean
        get() = ApplicationManager.getApplication().isWriteAccessAllowed

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable)
      }

      override fun toString() = "inWriteAction"
    })
  }
}
