// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.util.GithubAsyncUtil.extractError
import org.jetbrains.plugins.github.util.GithubAsyncUtil.isCancellation
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier

object GithubAsyncUtil {

  @JvmStatic
  fun <T> awaitFuture(progressIndicator: ProgressIndicator, future: Future<T>): T {
    var result: T
    while (true) {
      try {
        result = future.get(50, TimeUnit.MILLISECONDS)
        break
      }
      catch (e: TimeoutException) {
        progressIndicator.checkCanceled()
      }
      catch (e: Exception) {
        if (isCancellation(e)) throw ProcessCanceledException()
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
      if (error != null) {
        if (isCancellation(error)) handleToOtherIfCancelled(futureSupplier, other)
        other.completeExceptionally(error.cause)
      }
      other.complete(result)
    }
  }

  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }

  fun extractError(error: Throwable): Throwable {
    return when (error) {
      is CompletionException -> extractError(error.cause!!)
      is ExecutionException -> extractError(error.cause!!)
      else -> error
    }
  }
}

fun <T> ProgressManager.submitIOTask(progressIndicator: ProgressIndicator,
                                     task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> {
  return CompletableFuture.supplyAsync(Supplier { runProcess(Computable { task(progressIndicator) }, progressIndicator) },
                                       ProcessIOExecutorService.INSTANCE)
}

fun <T> ProgressManager.submitBackgroundTask(project: Project?,
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

fun <T> CompletableFuture<T>.handleOnEdt(parentDisposable: Disposable, handler: (T, Throwable?) -> Unit): CompletableFuture<Unit> {
  val handlerReference = AtomicReference(handler)
  Disposer.register(parentDisposable, Disposable {
    handlerReference.set(null)
  })

  return handleAsync(BiFunction<T, Throwable?, Unit> { result: T, error: Throwable? ->
    handlerReference.get()?.invoke(result, error?.let { extractError(it) })
  }, EDT_EXECUTOR)
}

fun <T, R> CompletableFuture<T>.handleOnEdt(handler: (T, Throwable?) -> R): CompletableFuture<R> =
  handleAsync(BiFunction<T, Throwable?, R> { result: T, error: Throwable? ->
    handler(result, error?.let { extractError(it) })
  }, EDT_EXECUTOR)

fun <T, R> CompletableFuture<T>.successAsync(executor: Executor, handler: (T) -> R): CompletableFuture<R> =
  handleAsync(BiFunction<T, Throwable?, R> { result: T, error: Throwable? ->
    if (error != null) throw extractError(error) else handler(result)
  }, executor)

fun <T, R> CompletableFuture<T>.successOnEdt(handler: (T) -> R): CompletableFuture<R> =
  successAsync(EDT_EXECUTOR, handler)

fun <T> CompletableFuture<T>.errorOnEdt(handler: (Throwable) -> T): CompletableFuture<T> =
  handleAsync(BiFunction<T, Throwable?, T> { result: T, error: Throwable? ->
    if (error != null) {
      val actualError = extractError(error)
      if (isCancellation(actualError)) throw ProcessCanceledException()
      handler(actualError)
    }
    result
  }, EDT_EXECUTOR)

val EDT_EXECUTOR = Executor { runnable -> runInEdt { runnable.run() } }