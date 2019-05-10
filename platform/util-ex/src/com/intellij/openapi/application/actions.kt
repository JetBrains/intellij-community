// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

inline fun <T> runWriteAction(crossinline runnable: () -> T): T {
  return ApplicationManager.getApplication().runWriteAction(Computable { runnable() })
}

inline fun <T> runUndoTransparentWriteAction(crossinline runnable: () -> T): T {
  return computeDelegated {
    CommandProcessor.getInstance().runUndoTransparentAction {
      ApplicationManager.getApplication().runWriteAction(Runnable { it(runnable()) })
    }
  }
}

inline fun <T> runReadAction(crossinline runnable: () -> T): T {
  return ApplicationManager.getApplication().runReadAction(Computable { runnable() })
}

/**
 * @suppress Internal use only
 */
fun <T> invokeAndWaitIfNeeded(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app == null) {
    if (SwingUtilities.isEventDispatchThread()) {
      return runnable()
    }
    else {
      @Suppress("UNCHECKED_CAST")
      try {
        return computeDelegated { SwingUtilities.invokeAndWait { it(runnable()) } }
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
    return computeDelegated { app.invokeAndWait({ it (runnable()) }, modalityState ?: ModalityState.defaultModalityState()) }
  }
}

@Deprecated(replaceWith = ReplaceWith("invokeAndWaitIfNeeded()"), message = "Use invokeAndWaitIfNeeded()")
fun <T> invokeAndWaitIfNeed(modalityState: ModalityState? = null, runnable: () -> T): T {
  return invokeAndWaitIfNeeded(modalityState, runnable)
}

@PublishedApi
internal inline fun <T> computeDelegated(executor: (setter: (T) -> Unit) -> Unit): T {
  var resultRef: T? = null
  executor { resultRef = it }
  @Suppress("UNCHECKED_CAST")
  return resultRef as T
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