// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.CancellationException

inline fun <reified T : Any> logger() = Logger.getInstance(T::class.java)

fun logger(category: String) = Logger.getInstance(category)

inline fun Logger.debug(e: Exception? = null, lazyMessage: () -> String) {
  if (isDebugEnabled) {
    debug(lazyMessage(), e)
  }
}

inline fun Logger.trace(lazyMessage: () -> String) {
  if (isTraceEnabled) {
    trace(lazyMessage())
  }
}

inline fun Logger.debugOrInfoIfTestMode(e: Exception? = null, lazyMessage: () -> String) {
  if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
    info(lazyMessage())
  }
  else {
    debug(e, lazyMessage)
  }
}

inline fun <T> Logger.runAndLogException(runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    error(e)
    return null
  }
}