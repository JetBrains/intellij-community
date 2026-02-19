// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.concurrency.withThreadLocal
import com.intellij.openapi.application.AccessToken
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.Assertions.assertInstanceOf

private val tlErrorLog: ThreadLocal<ErrorLog?> = ThreadLocal()

internal fun withErrorLog(errorLog: ErrorLog?): AccessToken {
  return withThreadLocal(tlErrorLog) {
    errorLog
  }
}

internal val errorLog: ErrorLog? get() = tlErrorLog.get()

/**
 * Collects errors which are logged inside the [Runnable] and re-throws them after [Runnable] finishes.
 */
fun assertNoErrorLogged(executable: Runnable) {
  val errors = collectErrorsLoggedInTheCurrentThread(executable::run)
  if (errors.isNotEmpty()) {
    rethrowLoggedErrors(null, errors)
  }
}

inline fun <reified T : Throwable> assertErrorLogged(noinline executable: () -> Unit): T {
  return assertErrorLogged(T::class.java, executable)
}

fun <T : Throwable> assertErrorLogged(clazz: Class<T>, executable: ThrowableRunnable<*>): T {
  return assertErrorLogged(clazz, executable::run)
}

@PublishedApi
internal fun <T : Throwable> assertErrorLogged(clazz: Class<T>, executable: () -> Unit): T {
  val errors = collectErrorsLoggedInTheCurrentThread(executable)
  return assertErrorLogged(clazz, errors)
}

private fun <T : Throwable> assertErrorLogged(clazz: Class<T>, errors: List<LoggedError>): T {
  when (errors.size) {
    0 -> {
      throw TestLoggerAssertionError("No errors were reported", null)
    }
    1 -> {
      return assertInstanceOf(clazz, errors.single().cause)
    }
    else -> {
      throw TestLoggerAssertionError("Multiple errors were reported", null).also {
        for (error in errors) {
          it.addSuppressed(error)
        }
      }
    }
  }
}

private fun collectErrorsLoggedInTheCurrentThread(executable: () -> Unit): List<LoggedError> {
  val errorLog = ErrorLog()
  withErrorLog(errorLog).use { _ ->
    executable()
  }
  return errorLog.takeLoggedErrors()
}

/**
 * Installs a thread-local [LoggedErrorProcessor] which collects errors
 * from [com.intellij.openapi.diagnostic.Logger.error] calls in the current thread.
 * If the [executable] completes with an exception, collected errors are added as [suppressed][addSuppressed] to it.
 * If the [executable] completes normally, collected errors are added as suppressed
 * to a fresh [TestLoggerAssertionError], which is then thrown.
 */
@Internal
fun <T> recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures(executable: () -> T): T {
  if (System.getProperty("intellij.testFramework.rethrow.logged.errors") == "true") {
    return executable()
  }
  val errorLog = ErrorLog()
  try {
    withErrorLog(errorLog).use { _ ->
      return executable()
    }
  }
  finally {
    errorLog.reportAsFailures()
  }
}

/**
 * An overload for Java.
 */
@Internal
fun recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures(executable: ThrowableRunnable<*>) {
  recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures(executable::run)
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

@Deprecated("Re-throwing from Logger.error() makes the test behaviour different from the production run")
@Throws(Exception::class)
fun rethrowLoggedErrorsIn(executable: ThrowableRunnable<*>) {
  withErrorLog(null).use { _ ->
    executable.run()
  }
}
