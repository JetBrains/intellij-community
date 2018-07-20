/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diagnostic

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