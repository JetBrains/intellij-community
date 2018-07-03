// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Ref
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.withContext
import org.jetbrains.concurrency.Obsolescent
import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.CoroutineContext

inline fun <T> runWriteAction(crossinline runnable: () -> T): T {
  return ApplicationManager.getApplication().runWriteAction(Computable { runnable() })
}

inline fun <T> runUndoTransparentWriteAction(crossinline runnable: () -> T): T {
  var result: T? = null
  CommandProcessor.getInstance().runUndoTransparentAction {
    result = ApplicationManager.getApplication().runWriteAction(Computable { runnable() })
  }
  @Suppress("UNCHECKED_CAST")
  return result as T
}

inline fun <T> runReadAction(crossinline runnable: () -> T): T = ApplicationManager.getApplication().runReadAction(Computable { runnable() })

/**
 * @exclude Internal use only
 */
fun <T> invokeAndWaitIfNeed(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app == null) {
    if (SwingUtilities.isEventDispatchThread()) {
      return runnable()
    }
    else {
      try {
        val resultRef = Ref.create<T>()
        SwingUtilities.invokeAndWait { resultRef.set(runnable()) }
        return resultRef.get()
      }
      catch (e: InvocationTargetException) {
        throw e.cause ?: e
      }
    }
  }
  else if (app.isDispatchThread) {
    return runnable()
  }
  else {
    val resultRef = Ref.create<T>()
    app.invokeAndWait({ resultRef.set(runnable()) }, modalityState ?: ModalityState.defaultModalityState())
    return resultRef.get()
  }
}

inline fun runInEdt(modalityState: ModalityState? = null, crossinline runnable: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    runnable()
  }
  else {
    app.invokeLater({ runnable() }, modalityState ?: ModalityState.defaultModalityState())
  }
}


// Coroutine API

suspend fun <T> asyncOnEdt(modalityState: ModalityState = ModalityState.defaultModalityState(),
                           block: suspend () -> T): T =
  withContext(ModalityStateContext(modalityState) + EventThreadContext, block = block)

/** Must be only used within a (direct on indirect) `asyncOnEdt { ... }` block. */
suspend fun <T> asyncWriteAction(block: suspend () -> T): T =
  withContext(WriteActionContext, block = block)

/** Must be only used within a (direct on indirect) `asyncOnEdt { ... }` block. */
suspend fun <T> asyncUndoTransparentWriteAction(block: suspend () -> T): T =
  withContext(UndoTransparentWriteActionContext, block = block)


internal abstract class ApplicationInvokeLaterDispatcher : CoroutineDispatcher() {

  private val CoroutineContext.modalityState: ModalityState
    get() {
      val edtContext = this[ModalityStateContext] ?: throw IllegalStateException("Not within ModalityStateContext")
      return edtContext.modalityState
    }

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val modalityState = context.modalityState
    val obsolescent = context[ObsolescentContext]?.obsolescent

    val blockToRun = obsolescent?.let {
      Runnable {
        if (it.isObsolete) context[Job]!!.cancel()
        block.run()
      }
    } ?: block
    runInEdt(modalityState) { dispatchInContext(context, blockToRun) }
  }

  protected abstract fun dispatchInContext(context: CoroutineContext, block: Runnable)
}

internal object EventThreadContext : ApplicationInvokeLaterDispatcher() {
  override fun dispatchInContext(context: CoroutineContext, block: Runnable) = block.run()
}

internal object WriteActionContext : ApplicationInvokeLaterDispatcher() {
  override fun dispatchInContext(context: CoroutineContext, block: Runnable) = ApplicationManager.getApplication().runWriteAction(block)
}

internal object UndoTransparentWriteActionContext : ApplicationInvokeLaterDispatcher() {
  override fun dispatchInContext(context: CoroutineContext, block: Runnable) = runUndoTransparentWriteAction { block.run() }
}

internal open class ModalityStateContext(val modalityState: ModalityState)
  : AbstractCoroutineContextElement(ModalityStateContext) {
  companion object Key : CoroutineContext.Key<ModalityStateContext>
}

internal open class ObsolescentContext(val obsolescent: Obsolescent) : AbstractCoroutineContextElement(ObsolescentContext) {
  companion object Key : CoroutineContext.Key<ObsolescentContext>

}

