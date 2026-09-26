// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * If someone uses coroutines' runTest, the coroutine exception handling machinery gets active and starts gathering all exceptions
 * that are thrown outside structured concurrency blocks.
 * In coroutines, such failures get reported at the start of the next `runTest`, but they do not logically correspond to the next test.
 * Instead, we choose to report them as standalone exceptions.
 */
internal class UnhandledCoroutineExceptionHandlerService : CoroutineExceptionHandler {
  private companion object {
    private val LOG = logger<UnhandledCoroutineExceptionHandlerService>()

    init {
      try {
        Class.forName("kotlinx.coroutines.test.TestScopeKt")
          .getDeclaredMethod("setCatchNonTestRelatedExceptions", Boolean::class.java)
          .invoke(null, false)
      } catch (e: ClassNotFoundException) {
        LOG.debug(e)
      } catch (e: ReflectiveOperationException) {
        LOG.warn("Failed to disable catching non-test related exceptions", e)
      }
    }
  }

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    // if errorLog is installed, then the exceptions will be caught by the corresponding `runTest` which is currently active
    if (errorLog != null) {
      return
    }
    // IJPL-246400
    // logAsTeamcityTestFailure(LoggedError(null, exception))
  }

  override val key: CoroutineContext.Key<*>
    get() = throw UnsupportedOperationException("This class cannot be used as a coroutine context element")
}
