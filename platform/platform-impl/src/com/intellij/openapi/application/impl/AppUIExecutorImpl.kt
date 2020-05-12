// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.constraints.Expiration
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * @author peter
 * @author eldar
 */
internal class AppUIExecutorImpl private constructor(private val modality: ModalityState,
                                                     private val thread: ExecutionThread,
                                                     constraints: Array<ContextConstraint>,
                                                     cancellationConditions: Array<BooleanSupplier>,
                                                     expirableHandles: Set<Expiration>)
  : AppUIExecutor,
    BaseExpirableExecutorMixinImpl<AppUIExecutorImpl>(constraints, cancellationConditions, expirableHandles,
                                                      getExecutorForThread(thread, modality)) {

  constructor(modality: ModalityState, thread: ExecutionThread) : this(modality, thread, emptyArray(), emptyArray(), emptySet())

  companion object {
    private fun getExecutorForThread(thread: ExecutionThread,
                                     modality: ModalityState) =
      when (thread) {
        ExecutionThread.EDT -> MyEdtExecutor(modality)
        ExecutionThread.WT -> MyWtExecutor(modality)
      }
  }

  private class MyWtExecutor(private val modality: ModalityState) : Executor {
    override fun execute(command: Runnable) {
      if (ApplicationManager.getApplication().isWriteThread
          && (ApplicationImpl.USE_SEPARATE_WRITE_THREAD
              || !TransactionGuard.getInstance().isWriteSafeModality(modality)
              || TransactionGuard.getInstance().isWritingAllowed)
          && !ModalityState.current().dominates(modality)) {
        command.run()
      }
      else {
        ApplicationManager.getApplication().invokeLaterOnWriteThread(command, modality)
      }
    }

  }
  private class MyEdtExecutor(private val modality: ModalityState) : Executor {
    override fun execute(command: Runnable) {
      if (ApplicationManager.getApplication().isDispatchThread
          && (!TransactionGuard.getInstance().isWriteSafeModality(modality)
              || TransactionGuard.getInstance().isWritingAllowed)
          && !ModalityState.current().dominates(modality)) {
        command.run()
      }
      else {
        ApplicationManager.getApplication().invokeLater(command, modality)
      }
    }
  }

  override fun cloneWith(constraints: Array<ContextConstraint>,
                         cancellationConditions: Array<BooleanSupplier>,
                         expirationSet: Set<Expiration>): AppUIExecutorImpl =
    AppUIExecutorImpl(modality, thread, constraints, cancellationConditions, expirationSet)

  override fun dispatchLaterUnconstrained(runnable: Runnable) =
    when (thread) {
      ExecutionThread.EDT -> ApplicationManager.getApplication().invokeLater(runnable, modality)
      ExecutionThread.WT -> ApplicationManager.getApplication().invokeLaterOnWriteThread(runnable, modality)
    }

  override fun later(): AppUIExecutorImpl {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else -1
    return withConstraint(object : ContextConstraint {
      @Volatile
      var usedOnce: Boolean = false

      override fun isCorrectContext(): Boolean =
        when (thread) {
          ExecutionThread.EDT -> when (edtEventCount) {
            -1 -> ApplicationManager.getApplication().isDispatchThread
            else -> usedOnce || edtEventCount != IdeEventQueue.getInstance().eventCount
          }
          ExecutionThread.WT -> usedOnce
        }

      override fun schedule(runnable: Runnable) {
        dispatchLaterUnconstrained(Runnable {
                                     usedOnce = true
                                     runnable.run()
                                   })
      }

      override fun toString() = "later"
    })
  }

  override fun withDocumentsCommitted(project: Project): AppUIExecutorImpl {
    return withConstraint(WithDocumentsCommitted(project, modality), project)
  }

  override fun inTransaction(parentDisposable: Disposable): AppUIExecutorImpl {
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

  @Deprecated("Beware, context might be infectious, if coroutine resumes other waiting coroutines. " +
              "Use runUndoTransparentWriteAction instead.", ReplaceWith("this"))
  fun inUndoTransparentAction(): AppUIExecutorImpl {
    return withConstraint(object : ContextConstraint {
      override fun isCorrectContext(): Boolean =
        CommandProcessor.getInstance().isUndoTransparentActionInProgress

      override fun schedule(runnable: Runnable) {
        CommandProcessor.getInstance().runUndoTransparentAction(runnable)
      }

      override fun toString() = "inUndoTransparentAction"
    })
  }

  @Deprecated("Beware, context might be infectious, if coroutine resumes other waiting coroutines. " +
              "Use runWriteAction instead.", ReplaceWith("this"))
  fun inWriteAction(): AppUIExecutorImpl {
    return withConstraint(object : ContextConstraint {
      override fun isCorrectContext(): Boolean =
        ApplicationManager.getApplication().isWriteAccessAllowed

      override fun schedule(runnable: Runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable)
      }

      override fun toString() = "inWriteAction"
    })
  }
  override fun inSmartMode(project: Project): AppUIExecutorImpl {
    return withConstraint(InSmartMode(project), project)
  }

}

@Deprecated("Beware, context might be infectious, if coroutine resumes other waiting coroutines. " +
            "Use runUndoTransparentWriteAction instead.", ReplaceWith("this"))
fun AppUIExecutor.inUndoTransparentAction(): AppUIExecutor =
  (this as AppUIExecutorImpl).inUndoTransparentAction()

@Deprecated("Beware, context might be infectious, if coroutine resumes other waiting coroutines. " +
            "Use runWriteAction instead.", ReplaceWith("this"))
fun AppUIExecutor.inWriteAction():AppUIExecutor =
  (this as AppUIExecutorImpl).inWriteAction()

fun AppUIExecutor.withConstraint(constraint: ContextConstraint): AppUIExecutor =
  (this as AppUIExecutorImpl).withConstraint(constraint)
fun AppUIExecutor.withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): AppUIExecutor =
  (this as AppUIExecutorImpl).withConstraint(constraint, parentDisposable)

/**
 * A [context][CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
 * Contains: [ContinuationInterceptor].
 */
fun AppUIExecutor.coroutineDispatchingContext(): ContinuationInterceptor =
  (this as AppUIExecutorImpl).asCoroutineDispatcher()


internal class WithDocumentsCommitted(private val project: Project, private val modality: ModalityState) : ContextConstraint {
  override fun isCorrectContext(): Boolean {
    val manager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    return !manager.isCommitInProgress && !manager.hasEventSystemEnabledUncommittedDocuments()
  }

  override fun schedule(runnable: Runnable) {
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, modality)
  }

  override fun toString(): String {
    val isCorrectContext = isCorrectContext()
    val details = if (!isCorrectContext) {
      val manager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
      ", isCommitInProgress()=${manager.isCommitInProgress}" +
      ", hasEventSystemEnabledUncommittedDocuments()=${manager.hasEventSystemEnabledUncommittedDocuments()}"
    }
    else ""
    return "withDocumentsCommitted {isCorrectContext()=$isCorrectContext$details}"
  }
}

internal class InSmartMode(private val project: Project) : ContextConstraint {
  override fun isCorrectContext(): Boolean =
    !DumbService.getInstance(project).isDumb

  override fun schedule(runnable: Runnable) {
    DumbService.getInstance(project).runWhenSmart(runnable)
  }

  override fun toString() = "inSmartMode"
}

internal enum class ExecutionThread {
  EDT, WT
}