// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.concurrency.withThreadLocal
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.ApiStatus.Internal

private val tlErrorLog: ThreadLocal<ErrorLog?> = ThreadLocal()

internal val errorLog: ErrorLog? get() = tlErrorLog.get()

/**
 * Installs a thread-local [LoggedErrorProcessor] which collects errors
 * from [com.intellij.openapi.diagnostic.Logger.error] calls in the current thread.
 * If the [executable] completes with an exception, collected errors are added as [suppressed][addSuppressed] to it.
 * If the [executable] completes normally, collected errors are added as suppressed
 * to a fresh [TestLoggerAssertionError], which is then thrown.
 */
@Internal
fun <T> rethrowErrorsLoggedInTheCurrentThread(executable: () -> T): T {
  if (System.getProperty("intellij.testFramework.rethrow.logged.errors") == "true") {
    return executable()
  }
  val errorLog = ErrorLog()
  val result: T = try {
    withThreadLocal(tlErrorLog) {
      errorLog
    }.use { _ ->
      executable()
    }
  }
  catch (t: Throwable) {
    val loggedErrors = errorLog.takeLoggedErrors()
    if (loggedErrors.isNotEmpty()) {
      rethrowLoggedErrors(testFailure = t, loggedErrors)
    }
    throw t
  }
  val loggedErrors = errorLog.takeLoggedErrors()
  if (loggedErrors.isNotEmpty()) {
    rethrowLoggedErrors(testFailure = null, loggedErrors)
  }
  return result
}

/**
 * An overload for Java.
 */
@Internal
fun rethrowErrorsLoggedInTheCurrentThread(executable: ThrowableRunnable<*>) {
  rethrowErrorsLoggedInTheCurrentThread(executable::run)
}

private fun rethrowLoggedErrors(
  testFailure: Throwable?,
  loggedErrors: List<LoggedError>,
): Nothing {
  require(loggedErrors.isNotEmpty())
  val throwable = if (testFailure == null) {
    val message = if (loggedErrors.size == 1) "Error was logged" else "Errors were logged"
    TestLoggerAssertionError(message, null)
  }
  else {
    testFailure
  }
  for (loggedError in loggedErrors) {
    throwable.addSuppressed(loggedError)
  }
  throw throwable
}
