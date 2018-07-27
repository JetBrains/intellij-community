// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaGetter

inline fun <reified T : Any> logger(): Logger = Logger.getInstance(T::class.java)

fun logger(category: String): Logger = Logger.getInstance(category)

/**
 * Get logger instance to be used in Kotlin package methods. Usage:
 * ```
 * private val LOG: Logger get() = logger(::LOG)  // define at top level of the file containing the function
 * ```
 * In Kotlin 1.1 even simpler declaration will be possible:
 * ```
 * private val LOG: Logger = logger(::LOG)
 * ```
 * Notice explicit type declaration which can't be skipped in this case.
 */
fun logger(field: KProperty<Logger>): Logger = Logger.getInstance(field.javaGetter!!.declaringClass)

inline fun Logger.debug(e: Exception? = null, lazyMessage: () -> String) {
  if (isDebugEnabled) {
    debug(lazyMessage(), e)
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
    return null
  }
  catch (e: Throwable) {
    error(e)
    return null
  }
}