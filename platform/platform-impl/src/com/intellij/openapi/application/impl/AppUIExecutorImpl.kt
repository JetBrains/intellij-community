// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.constraints.Expiration
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
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
    private fun getExecutorForThread(thread: ExecutionThread, modality: ModalityState): Executor {
      return when (thread) {
        ExecutionThread.EDT -> MyEdtExecutor(modality)
        ExecutionThread.WT -> MyWtExecutor(modality)
      }
    }
  }

  private class MyWtExecutor(private val modality: ModalityState) : Executor {
    override fun execute(command: Runnable) {
      if (ApplicationManager.getApplication().isWriteIntentLockAcquired
          && (!TransactionGuard.getInstance().isWriteSafeModality(modality)
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
      // TransactionGuard.isWritingAllowed could throw exception if there is no write intent lock
      // It was always so, but now we could be here without WIL
      if (ApplicationManager.getApplication().isDispatchThread
          && ApplicationManager.getApplication().isWriteIntentLockAcquired
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
                         expirationSet: Set<Expiration>): AppUIExecutorImpl {
    return AppUIExecutorImpl(modality, thread, constraints, cancellationConditions, expirationSet)
  }

  override fun dispatchLaterUnconstrained(runnable: Runnable) {
    return when (thread) {
      ExecutionThread.EDT -> ApplicationManager.getApplication().invokeLater(runnable, modality)
      ExecutionThread.WT -> ApplicationManager.getApplication().invokeLaterOnWriteThread(runnable, modality)
    }
  }

  override fun later(): AppUIExecutorImpl {
    val edtEventCount = if (ApplicationManager.getApplication().isDispatchThread) IdeEventQueue.getInstance().eventCount else -1
    return withConstraint(object : ContextConstraint {
      @Volatile
      var usedOnce: Boolean = false

      override fun isCorrectContext(): Boolean {
        return when (thread) {
          ExecutionThread.EDT -> when (edtEventCount) {
            -1 -> ApplicationManager.getApplication().isDispatchThread
            else -> usedOnce || edtEventCount != IdeEventQueue.getInstance().eventCount
          }
          ExecutionThread.WT -> usedOnce
        }
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

  override fun inSmartMode(project: Project): AppUIExecutorImpl {
    return withConstraint(InSmartMode(project), project)
  }
}

fun AppUIExecutor.withConstraint(constraint: ContextConstraint): AppUIExecutor {
  return (this as AppUIExecutorImpl).withConstraint(constraint)
}

fun AppUIExecutor.withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): AppUIExecutor {
  return (this as AppUIExecutorImpl).withConstraint(constraint, parentDisposable)
}

/**
 * A [context][CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
 * Contains: [ContinuationInterceptor].
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  message = "Do not use: coroutine cancellation must not be handled by a dispatcher. " +
            "Use Dispatchers.Main and ModalityState.asContextElement() if needed",
)
fun AppUIExecutor.coroutineDispatchingContext(): ContinuationInterceptor {
  return (this as AppUIExecutorImpl).asCoroutineDispatcher()
}

internal class WithDocumentsCommitted(private val project: Project, private val modality: ModalityState) : ContextConstraint {
  override fun isCorrectContext(): Boolean {
    val manager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    return !manager.isCommitInProgress && !manager.hasEventSystemEnabledUncommittedDocuments()
  }

  override fun schedule(runnable: Runnable) {
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(modality, runnable)
  }

  override fun toString(): String {
    val isCorrectContext = isCorrectContext()
    val details = if (isCorrectContext) {
      ""
    }
    else {
      val manager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
      ", isCommitInProgress()=${manager.isCommitInProgress}" +
      ", hasEventSystemEnabledUncommittedDocuments()=${manager.hasEventSystemEnabledUncommittedDocuments()}"
    }
    return "withDocumentsCommitted {isCorrectContext()=$isCorrectContext$details}"
  }
}

internal class InSmartMode(private val project: Project) : ContextConstraint {
  init {
    check(!LightEdit.owns(project)) {
      "InSmartMode can't be used in LightEdit mode, check that LightEdit.owns(project)==false before calling"
    }
  }

  override fun isCorrectContext(): Boolean {
    val correctContext = !project.isDisposed && DumbServiceImpl.getInstance(project).runWhenSmartCondition.asBoolean
    if (!correctContext) {
      thisLogger().debug("InSmartMode dispatched")
    }
    return correctContext
  }

  override fun schedule(runnable: Runnable) {
    DumbService.getInstance(project).runWhenSmart(runnable)
  }

  override fun toString(): String = "inSmartMode"
}

internal enum class ExecutionThread {
  EDT, WT
}
