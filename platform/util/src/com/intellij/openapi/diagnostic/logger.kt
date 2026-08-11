// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.util.ExceptionUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
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
inline fun fileLogger(): Logger {
  return currentClassLogger()
}

inline fun Logger.debug(t: Throwable? = null, lazyMessage: () -> @NonNls String) {
  if (isDebugEnabled) {
    debug(lazyMessage(), t)
  }
}

/**
 * Logs a stable, constant [message] at ERROR level and the variable [details] at WARN level.
 *
 * A `Logger.error` report is turned into a CI test failure named after its message. When the message
 * embeds per-occurrence diagnostics, otherwise-identical failures stop merging into a single one. This
 * helper keeps the ERROR [message] constant — so the failures group together — while the variable
 * [details] go to the log and are attached to the error report (as an [Attachment]) so they can be
 * submitted to Diogen.
 *
 * Use this instead of interpolating variable data into a `Logger.error` message.
 */
fun Logger.errorWithWarnDetails(message: @NonNls String, details: @NonNls String, t: Throwable? = null) {
  warn(message, details, t)
  val attachment = Attachment("details.txt", details).also { it.isIncluded = true }
  if (t == null) error(message, attachment)
  else error(message, t, attachment)
}

/**
 * Throws an exception with stable [message] with the variable [details] attached (see [errorWithWarnDetails]),
 *
 * Use for unrecoverable "should never happen" conditions where the caller cannot meaningfully continue.
 */
@Contract("_, _, _ -> fail")
fun Logger.fatalErrorWithWarnDetails(message: @NonNls String, details: @NonNls String, t: Throwable? = null): Nothing {
  warn(message, details, t)
  val attachment = Attachment("details.txt", details).also { it.isIncluded = true }
  throw RuntimeExceptionWithAttachments(message, t, attachment)
}

private fun Logger.warn(message: @NonNls String, details: @NonNls String, t: Throwable?) = warn("$message: $details", t)

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
fun <T> Result<T>.getOrLogException(logger: Logger): T? {
  return getOrHandleException {
    logger.error(it)
  }
}

/**
 * @see getOrHandleException
 */
@Deprecated(
  message = "The name is misleading, as the handler can do anything, not just log",
  replaceWith = ReplaceWith("getOrHandleException"),
)
@Internal
inline fun <T> Result<T>.getOrLogException(log: (Throwable) -> Unit): T? = getOrHandleException(log)

/**
 * Returns the result value if it's a success or calls the given [handler] and returns null if it's a failure.
 *
 * If the result is a success, its value is returned and the handler is not called.
 *
 * If the result is a failure, and the exception is a control flow exception ([CancellationException] or [ControlFlowException]),
 * then the exception is rethrown and the current stack trace is added to it as a suppressed exception.
 *
 * If the result is a failure, and the exception is not a control flow exception,
 * then the given [handler] is called and `null` is returned.
 */
inline fun <T> Result<T>.getOrHandleException(handler: (Throwable) -> Unit): T? {
  return onFailure { e ->
    rethrowControlFlowException(e)
    handler(e)
  }.getOrNull()
}

/**
 * Rethrows the given exception [e] if it's a _control flow exception_.
 *
 * _Control flow exceptions_ are:
 * - [CancellationException] (including [ProcessCanceledException][com.intellij.openapi.progress.ProcessCanceledException])
 * - [ControlFlowException]
 *
 * The current stack trace is added to the rethrown exception as a suppressed exception.
 *
 * If [e] is null, then this function is a no-op.
 */
fun rethrowControlFlowException(e: Throwable?) {
  if (e != null && Logger.isRethrowable(e)) {
    throw ExceptionUtilRt.addRethrownStackAsSuppressed(e)
  }
}
