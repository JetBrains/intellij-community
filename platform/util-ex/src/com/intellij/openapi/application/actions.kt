// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.ApiStatus.Internal

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

inline fun assertReadAccessAllowed() {
  ApplicationManager.getApplication().assertReadAccessAllowed()
}

inline fun assertWriteAccessAllowed() {
  ApplicationManager.getApplication().assertWriteAccessAllowed()
}

/**
 * @suppress Internal use only
 */
@Internal
fun <T> invokeAndWaitIfNeeded(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    return runnable()
  }
  else {
    return computeDelegated {
      app.invokeAndWait({ it (runnable()) }, modalityState ?: ModalityState.defaultModalityState())
    }
  }
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
    invokeLater(modalityState, runnable)
  }
}

inline fun invokeLater(modalityState: ModalityState? = null, crossinline runnable: () -> Unit) {
  ApplicationManager.getApplication().invokeLater({ runnable() }, modalityState ?: ModalityState.defaultModalityState())
}
