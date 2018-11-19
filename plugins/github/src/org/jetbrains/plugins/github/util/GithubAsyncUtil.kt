// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.*
import java.util.function.BiFunction

object GithubAsyncUtil {

  /**
   * Run [consumer] on EDT with the result of [future]
   * If future is cancelled, [consumer] will not be executed
   *
   * This is a naive implementation with timeout waiting
   */
  @JvmStatic
  fun <R : Future<T>, T> awaitFutureAndRunOnEdt(future: R,
                                                project: Project, title: String, errorTitle: String,
                                                consumer: (T) -> Unit) {
    object : Task.Backgroundable(project, title, true, PerformInBackgroundOption.DEAF) {
      var result: T? = null

      override fun run(indicator: ProgressIndicator) {
        while (true) {
          try {
            result = future.get(50, TimeUnit.MILLISECONDS)
            break
          }
          catch (e: TimeoutException) {
            indicator.checkCanceled()
          }
        }
        indicator.checkCanceled()
      }

      override fun onSuccess() {
        result?.let(consumer)
      }

      override fun onThrowable(error: Throwable) {
        if (isCancellation(error)) return
        GithubNotifications.showError(project, errorTitle, error)
      }
    }.queue()
  }

  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }
}

fun <T> CompletableFuture<T>.handleOnEdt(handler: (T?, Throwable?) -> Unit): CompletableFuture<Unit> =
  handleAsync(BiFunction<T?, Throwable?, Unit> { result: T?, error: Throwable? ->
    handler(result, error)
  }, EDT_EXECUTOR)

val EDT_EXECUTOR = Executor { runnable -> runInEdt { runnable.run() } }