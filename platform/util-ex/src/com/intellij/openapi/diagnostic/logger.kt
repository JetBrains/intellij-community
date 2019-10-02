// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import java.lang.reflect.Member
import java.util.concurrent.CancellationException
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

inline fun <reified T : Any> logger() = Logger.getInstance(T::class.java)

fun logger(category: String) = Logger.getInstance(category)

/**
 * Get logger instance to be used in Kotlin package methods. Usage:
 * ```
 * private val LOG: Logger = logger(::LOG) // define at top level of the file containing the function
 * ```

 * Notice explicit type declaration which can't be skipped in this case.
 */
fun logger(field: KProperty<Logger>) = Logger.getInstance(field.declaringClass)

private val KProperty<*>.declaringClass: Class<*> get() = (javaField ?: javaGetter as? Member)?.declaringClass!!

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