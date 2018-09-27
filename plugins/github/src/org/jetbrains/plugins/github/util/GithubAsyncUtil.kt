// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.*
import java.util.function.BiFunction

object GithubAsyncUtil {

  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }
}

/**
 * Handle on EDT if [disposable] is not disposed and [condition] is satisfied
 */
fun <T> CompletableFuture<T>.handleOnEdt(handler: (T?, Throwable?) -> Unit): CompletableFuture<Unit> =
  handleAsync(BiFunction<T?, Throwable?, Unit> { result: T?, error: Throwable? ->
    handler(result, error)
  }, EDT_EXECUTOR)

val EDT_EXECUTOR = Executor { runnable -> runInEdt { runnable.run() } }