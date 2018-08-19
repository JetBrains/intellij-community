// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Ref
import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

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