// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

class EdtTestUtil {
  companion object {
    @TestOnly @JvmStatic fun <V> runInEdtAndGet(computable: ThrowableComputable<V, Throwable>): V =
      runInEdtAndGet { computable.compute() }

    @TestOnly @JvmStatic fun runInEdtAndWait(runnable: ThrowableRunnable<Throwable>) {
      runInEdtAndWait { runnable.run() }
    }
  }
}

/**
 * Consider using Kotlin coroutines and `com.intellij.openapi.application.AppUIExecutor.onUiThread().coroutineDispatchingContext()`
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
fun <V> runInEdtAndGet(compute: () -> V): V {
  var v : V? = null
  runInEdtAndWait { v = compute() }
  return v!!
}

/**
 * Consider using Kotlin coroutines and `com.intellij.openapi.application.AppUIExecutor.onUiThread().coroutineDispatchingContext()`
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
fun runInEdtAndWait(runnable: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app is Application) {
    if (app.isDispatchThread) {
      // reduce stack trace
      runnable()
    }
    else {
      var exception: Throwable? = null
      app.invokeAndWait {
        try {
          runnable()
        }
        catch (e: Throwable) {
          exception = e
        }
      }

      exception?.let { throw it }
    }
    return
  }

  if (SwingUtilities.isEventDispatchThread()) {
    runnable()
  }
  else {
    try {
      SwingUtilities.invokeAndWait { runnable() }
    }
    catch (e: InvocationTargetException) {
      throw e.cause ?: e
    }
  }
}