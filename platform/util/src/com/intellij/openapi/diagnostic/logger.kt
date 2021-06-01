// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException

inline fun <reified T : Any> @Suppress("unused") T.thisLogger() = Logger.getInstance(T::class.java)

inline fun <reified T : Any> logger() = Logger.getInstance(T::class.java)

@Deprecated(level = DeprecationLevel.ERROR, message = "Use Logger directly", replaceWith = ReplaceWith("Logger.getInstance(category)"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
fun logger(@NonNls category: String) = Logger.getInstance(category)

inline fun Logger.debug(e: Exception? = null, lazyMessage: () -> @NonNls String) {
  if (isDebugEnabled) {
    debug(lazyMessage(), e)
  }
}

inline fun Logger.trace(@NonNls lazyMessage : () -> String) {
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
    when (e) {
      is ProcessCanceledException,
      is CancellationException -> throw e
      else -> logger.error(e)
    }
  }.getOrNull()
}
