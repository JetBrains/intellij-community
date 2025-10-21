// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.util.ExceptionUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.lang.invoke.MethodHandles
import java.util.concurrent.CancellationException

/**
 * Returns a logger that corresponds to the [T] class.
 */
inline fun <reified T : Any> logger(): Logger = Logger.getInstance(T::class.java)

/**
 * Returns a logger that corresponds to the [T] class inferred from the receiver.
 *
 * A shortcut to [logger] to avoid writing the type parameter by hand.
 *
 * Note: this method only uses [this] value to infer the type parameter [T].
 * It does not use the **actual** runtime class (`this::class`) of the receiver value.
 */
inline fun <reified T : Any> T.thisLogger(): Logger = Logger.getInstance(T::class.java)

/**
 * Returns a logger that corresponds to the class of the caller method.
 * 
 * Useful for getting logger for global functions without passing a class or package
 * 
 * This function MUST be inline to properly get the calling class.
 */
@Suppress("NOTHING_TO_INLINE")
@Internal
inline fun currentClassLogger(): Logger {
  val clazz = MethodHandles.lookup().lookupClass()
  return Logger.getInstance(clazz)
}

/**
 * Returns a logger corresponding to the current file if called in a global context like a global function or a global initializer.
 *
 * It returns a logger with a real class category if it's called inside a real class. No checks are being performed on this.
 *
 * A shortcut to [currentClassLogger].
 * 
 * This function MUST be inline to properly get the calling class.
 *
 * Example:
 * ```
 * // file level member
 * private val logger = fileLogger()
 * ```
 */
@Suppress("NOTHING_TO_INLINE")
@Internal
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

/* Cannot name it `trace` due to the clash with the function above */
inline fun Logger.traceThrowable(lazyThrowable: () -> Throwable) {
  if (isTraceEnabled) {
    return trace(lazyThrowable())
  }
}

/** Consider using [Result.getOrHandleException] for more straight-forward API instead. */
@Internal
inline fun <T> Logger.runAndLogException(runnable: () -> T): T? {
  return runCatching {
    runnable()
  }.getOrLogException(this)
}

/**
 * Returns the result value if it's a success, or logs the exception returns null if it's a failure.
 *
 * Control flow exceptions are rethrown, not logged. See [Result.getOrHandleException] for details.
 *
 * Consider using [Result.getOrHandleException] to have more control over how the exception is handled.
 * Especially consider passing a custom message to the logger, not just the exception.
 */
@Internal
fun <T> Result<T>.getOrLogException(logger: Logger): T? {
  return getOrHandleException {
    logger.error(it)
  }
}

/**
 * Returns the result value if it's a success, or calls the given handler and returns null if it's a failure.
 *
 * If the result is a success, its value is returned and the handler is not called.
 *
 * If the result is a failure, and the exception is a control flow exception (`CancellationException` or `ControlFlowException`),
 * then the exception is rethrown and the current stack trace is added to it as a suppressed exception.
 *
 * If the result is a failure, and the exception is not a control flow exception,
 * then the given [handler] is called and `null` is returned.
 */
@Internal
@Deprecated(
  "The name is misleading, as the handler can do anything, not just log",
  replaceWith = ReplaceWith("getOrHandleException")
)
inline fun <T> Result<T>.getOrLogException(log: (Throwable) -> Unit): T? = getOrHandleException(log)

@Internal
inline fun <T> Result<T>.getOrHandleException(handler: (Throwable) -> Unit): T? {
  return onFailure { e ->
    rethrowControlFlowException(e)
    handler(e)
  }.getOrNull()
}

/**
 * Rethrows the given exception if it's a control flow exception.
 *
 * The control flow exceptions are currently defined as [CancellationException]
 * (including [com.intellij.openapi.progress.ProcessCanceledException])
 * and anything marked [ControlFlowException].
 *
 * The current stack trace is added to the rethrown exception as a suppressed exception.
 *
 * @param e the exception (`null` means do nothing)
 */
@Internal
fun rethrowControlFlowException(e: Throwable?) {
  if (e is CancellationException || e is ControlFlowException) {
    throw ExceptionUtilRt.addRethrownStackAsSuppressed(e)
  }
}
