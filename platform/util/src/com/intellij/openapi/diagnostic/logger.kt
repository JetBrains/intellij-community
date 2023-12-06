// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.annotations.NonNls
import java.lang.invoke.MethodHandles
import java.util.concurrent.CancellationException

inline fun <reified T : Any> T.thisLogger(): Logger = Logger.getInstance(T::class.java)

inline fun <reified T : Any> logger(): Logger = Logger.getInstance(T::class.java)

/**
 * Returns a logger that corresponds to the class of the caller method.
 * 
 * Useful for getting logger for global functions without passing a class or package
 * 
 * This function MUST be inline in order to properly obtain the calling class.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun currentClassLogger(): Logger {
  val clazz = MethodHandles.lookup().lookupClass()
  return Logger.getInstance(clazz)
}

/**
 * Returns a logger corresponding to the current file if called in global context like a global function or a global initializer.
 *
 * It returns a logger with a real class category if it's called inside a real class. No checks are being performed on this.
 *
 * A shortcut to [currentClassLogger].
 * 
 * This function MUST be inline in order to properly obtain the calling class.
 *
 * Example:
 * ```
 * // file level member
 * private val logger = fileLogger()
 * ```
 */
@Suppress("NOTHING_TO_INLINE")
inline fun fileLogger(): Logger {
  return currentClassLogger()
}

inline fun Logger.debug(e: Exception? = null, lazyMessage: () -> @NonNls String) {
  if (isDebugEnabled) {
    debug(lazyMessage(), e)
  }
}

inline fun Logger.trace(@NonNls lazyMessage: () -> String) {
  if (isTraceEnabled) {
    trace(lazyMessage())
  }
}

/** Consider using [Result.getOrLogException] for more straight-forward API instead. */
inline fun <T> Logger.runAndLogException(runnable: () -> T): T? {
  return runCatching {
    runnable()
  }.getOrLogException(this)
}

fun <T> Result<T>.getOrLogException(logger: Logger): T? {
  return onFailure { e ->
    if (e is ProcessCanceledException || e is CancellationException) {
      throw e
    }
    else {
      logger.error(e)
    }
  }.getOrNull()
}