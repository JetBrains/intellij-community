// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.*
import java.util.function.BiFunction

object GithubAsyncUtil {

  @JvmStatic
  fun <T> awaitMutableFuture(progressIndicator: ProgressIndicator, futureSupplier: () -> Future<T>): T {
    var result: T
    var future = futureSupplier()
    while (true) {
      try {
        result = future.get(50, TimeUnit.MILLISECONDS)
        break
      }
      catch (e: TimeoutException) {
        progressIndicator.checkCanceled()
      }
      catch (e: Exception) {
        if (isCancellation(e)) {
          future = futureSupplier()
          continue
        }
        if (e is ExecutionException) throw e.cause ?: e
        throw e
      }
    }
    return result
  }

  @JvmStatic
  fun <T> futureOfMutable(futureSupplier: () -> CompletableFuture<T>): CompletableFuture<T> {
    val result = CompletableFuture<T>()
    handleToOtherIfCancelled(futureSupplier, result)
    return result
  }

  private fun <T> handleToOtherIfCancelled(futureSupplier: () -> CompletableFuture<T>, other: CompletableFuture<T>) {
    futureSupplier().handle { result, error ->
      if (result != null) other.complete(result)
      if (error != null) {
        if (isCancellation(error)) handleToOtherIfCancelled(futureSupplier, other)
        other.completeExceptionally(error.cause)
      }
    }
  }

  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }
}

fun <T> ProgressManager.submitBackgroundTask(project: Project,
                                             title: String,
                                             canBeCancelled: Boolean,
                                             progressIndicator: ProgressIndicator,
                                             process: (indicator: ProgressIndicator) -> T): CompletableFuture<T> {
  val future = CompletableFuture<T>()
  runProcessWithProgressAsynchronously(object : Task.Backgroundable(project, title, canBeCancelled) {
    override fun run(indicator: ProgressIndicator) {
      future.complete(process(indicator))
    }

    override fun onCancel() {
      future.cancel(true)
    }

    override fun onThrowable(error: Throwable) {
      future.completeExceptionally(error)
    }
  }, progressIndicator)
  return future
}

fun <T> CompletableFuture<T>.handleOnEdt(handler: (T?, Throwable?) -> Unit): CompletableFuture<Unit> =
  handleAsync(BiFunction<T?, Throwable?, Unit> { result: T?, error: Throwable? ->
    handler(result, error)
  }, EDT_EXECUTOR)

val EDT_EXECUTOR = Executor { runnable -> runInEdt { runnable.run() } }