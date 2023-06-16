// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@ApiStatus.Internal
@ApiStatus.Experimental
@Deprecated("Use coroutines instead.")
object Futures {

  /**
   * Check is the exception is a cancellation signal.
   */
  @Deprecated(
    "- InterruptedException is an error which should be handled accordingly.\n" +
    "- In a coroutine, catch and re-throw CancellationException explicitly, as PCE is considered an error in a coroutine.\n" +
    "- In blocking code, catch and re-throw ProcessCanceledException explicitly, as CE is considered an error in blocking code.\n",
  )
  @JvmStatic
  fun isCancellation(error: Throwable): Boolean {
    @Suppress("DEPRECATION")
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }
}
