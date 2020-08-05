// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
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

  @Deprecated("Background process value now always drops on PCE")
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
                                     task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> =
  CompletableFuture.supplyAsync(Supplier { runProcess(Computable { task(progressIndicator) }, progressIndicator) },
                                ProcessIOExecutorService.INSTANCE)

fun <T> ProgressManager.submitIOTask(indicatorProvider: ProgressIndicatorsProvider,
                                     task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> {
  val indicator = indicatorProvider.acquireIndicator()
  return submitIOTask(indicator, task).whenComplete { _, _ ->
    indicatorProvider.releaseIndicator(indicator)
  }
}

fun <T> CompletableFuture<T>.handleOnEdt(disposable: Disposable,
                                         handler: (T?, Throwable?) -> Unit): CompletableFuture<Unit> {
  val handlerReference = AtomicReference(handler)
  Disposer.register(disposable, Disposable {
    handlerReference.set(null)
  })

  return handleAsync(BiFunction<T?, Throwable?, Unit> { result: T?, error: Throwable? ->
    val handlerFromRef = handlerReference.get() ?: throw ProcessCanceledException()
    handlerFromRef(result, error?.let { extractError(it) })
  }, getEDTExecutor(null))
}

fun <T, R> CompletableFuture<T>.handleOnEdt(modalityState: ModalityState? = null,
                                            handler: (T?, Throwable?) -> R): CompletableFuture<R> =
  handleAsync(BiFunction<T?, Throwable?, R> { result: T?, error: Throwable? ->
    handler(result, error?.let { extractError(it) })
  }, getEDTExecutor(modalityState))

fun <T, R> CompletableFuture<T>.successOnEdt(modalityState: ModalityState? = null, handler: (T) -> R): CompletableFuture<R> =
  handleOnEdt(modalityState) { result, error ->
    @Suppress("UNCHECKED_CAST")
    if (error != null) throw extractError(error) else handler(result as T)
  }

fun <T> CompletableFuture<T>.errorOnEdt(modalityState: ModalityState? = null,
                                        handler: (Throwable) -> Unit): CompletableFuture<T> =
  handleOnEdt(modalityState) { result, error ->
    if (error != null) {
      val actualError = extractError(error)
      if (isCancellation(actualError)) throw ProcessCanceledException()
      handler(actualError)
      throw actualError
    }
    @Suppress("UNCHECKED_CAST")
    result as T
  }

fun <T> CompletableFuture<T>.cancellationOnEdt(modalityState: ModalityState? = null,
                                               handler: (ProcessCanceledException) -> Unit): CompletableFuture<T> =
  handleOnEdt(modalityState) { result, error ->
    if (error != null) {
      val actualError = extractError(error)
      if (isCancellation(actualError)) handler(ProcessCanceledException())
      throw actualError
    }
    @Suppress("UNCHECKED_CAST")
    result as T
  }

fun <T> CompletableFuture<T>.completionOnEdt(modalityState: ModalityState? = null,
                                             handler: () -> Unit): CompletableFuture<T> =
  handleOnEdt(modalityState) { result, error ->
    @Suppress("UNCHECKED_CAST")
    if (error != null) {
      if (!isCancellation(error)) handler()
      throw extractError(error)
    }
    else {
      handler()
      result as T
    }
  }

fun getEDTExecutor(modalityState: ModalityState? = null) = Executor { runnable -> runInEdt(modalityState) { runnable.run() } }