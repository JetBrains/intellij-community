// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Function


/**
 * Provides utilities for integration of [CompletableFuture]s
 * with platform concurrency API, e.g. EDT, Read/Write actions, progress manager, etc.
 *
 * For each method, there is an idiomatic overload for Java.
 *
 * @see "com.intellij.collaboration.async.CompletableFutureUtil"
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Deprecated("Use coroutines instead.")
object Futures {

  /* ------------------------------------------------------------------------------------------- */
  //region Executors

  /**
   * Is used to specify that the action should be executed on the EDT.
   *
   * ```
   * calcSmthAsync(params) // executed on a pooled thread
   *   .thenAcceptAsync(it -> showInUI(it), Futures.inEdt()) // on EDT
   * ```
   */
  @JvmStatic
  @JvmOverloads
  fun inEdt(modalityState: ModalityState? = null): Executor = Executor { runnable -> runInEdt(modalityState) { runnable.run() } }

  /**
   * Is used to specify that the action should be executed on the EDT inside write action.
   *
   * ```
   * calcSmthAsync(params) // executed on a pooled thread
   *   .thenAcceptAsync(   // executed inside write action
   *     it -> writeInDocument(it),
   *     Futures.inWriteAction())
   * ```
   */
  @JvmStatic
  @JvmOverloads
  fun inWriteAction(modalityState: ModalityState? = null): Executor = Executor { runnable ->
    runInEdt(modalityState) {
      ApplicationManager.getApplication().runWriteAction(runnable)
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region Error handling utils

  /**
   * Check is the exception is a cancellation signal
   */
  @JvmStatic
  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }

  /**
   * If the given future failed with exception, and it wasn't a cancellation, logs the exception to the IDEA logger.
   *
   * ```java
   *   futuresChain
   *     .whenComplete(Futures.logIfFailed(SomeClass.class))
   * ```
   * or
   * ```java
   *   futuresChain
   *     .whenComplete(Futures.logIfFailed(SomeClass.class,
   *                                       it -> addAttachments(it))
   * ```
   * @param transformException One may want to wrap the actual exception with
   * [com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments] to additionally provide some diagnostic info
   */
  @JvmStatic
  @JvmOverloads
  inline fun logIfFailed(
    loggingClass: Class<*>,
    crossinline transformException: (Throwable) -> Throwable = { it }
  ): BiConsumer<Any?, Throwable> {
    return BiConsumer { _: Any?, cause: Throwable? ->
      if (cause != null && !isCancellation(cause)) {
        Logger.getInstance(loggingClass).error(transformException(cause))
      }
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region CompletableFuture integration with platform progress

  /**
   * Runs given [action] inside the [Task.Backgroundable] and returns its result as [CompletableFuture]
   */
  @JvmStatic
  @JvmOverloads
  fun <T> runProgressInBackground(
    project: Project,
    @NlsContexts.ProgressTitle title: String,
    canBeCancelled: Boolean = true,
    performInBackgroundOption: PerformInBackgroundOption? = null,
    onCancel: Runnable? = null,
    action: Function<in ProgressIndicator, out T>
  ): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled, performInBackgroundOption) {
        var value: Result<T>? = null

        override fun run(indicator: ProgressIndicator) {
          try {
            value = Result.success(action.apply(indicator))
          }
          catch (t: Throwable) {
            value = Result.failure(t)
          }
        }

        override fun onCancel(): Unit = onCancel?.run() ?: Unit
        override fun onSuccess() = future.waitForProgressBarCloseAndComplete(value!!.getOrThrow())
        override fun onThrowable(error: Throwable) = future.waitForProgressBarCloseAndCompleteExceptionally(error)
      })
    }
    return future
  }

  /**
   * Runs given asynchronous [action] and waits for its result inside the [Task.Backgroundable],
   * then returns the result as [CompletableFuture]
   */
  @JvmStatic
  @JvmOverloads
  fun <T> runAsyncProgressInBackground(
    project: Project,
    title: @NlsContexts.ProgressTitle String,
    canBeCancelled: Boolean = true,
    performInBackgroundOption: PerformInBackgroundOption? = null,
    onCancel: Runnable? = null,
    action: Function<in ProgressIndicator, out CompletableFuture<T>>
  ): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    runAsyncProgressInBackground(future, project, title, canBeCancelled, performInBackgroundOption, onCancel, action)
    return future
  }

  /**
   * Runs given asynchronous [action] and waits for its result inside the [Task.Backgroundable]
   */
  @JvmStatic
  @JvmOverloads
  fun <T> runAsyncProgressInBackground(
    future: CompletableFuture<T>,
    project: Project,
    title: @NlsContexts.ProgressTitle String,
    canBeCancelled: Boolean = true,
    performInBackgroundOption: PerformInBackgroundOption? = null,
    onCancel: Runnable? = null,
    action: Function<in ProgressIndicator, out CompletableFuture<T>>
  ) {
    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled, performInBackgroundOption) {
        var value: Result<T>? = null

        override fun run(indicator: ProgressIndicator) {
          try {
            val actionFuture = action.apply(indicator)
            actionFuture.join()
            value = Result.success(actionFuture.get())
          }
          catch (t: Throwable) {
            value = Result.failure(t)
          }
        }

        override fun onCancel(): Unit = onCancel?.run() ?: Unit
        override fun onSuccess() = future.waitForProgressBarCloseAndComplete(value!!.getOrThrow())
        override fun onThrowable(error: Throwable) = future.waitForProgressBarCloseAndCompleteExceptionally(error)
      })
    }
  }

  /**
   * We need to wait until the progress bar will be hidden
   * what is periodically done by InfoAndProgressPanel#myUpdateQueue every 50ms
   */
  private fun <T> CompletableFuture<T>.waitForProgressBarCloseAndComplete(value: T) {
    completeOnTimeout(value, 55, TimeUnit.MILLISECONDS)
  }

  private fun CompletableFuture<*>.waitForProgressBarCloseAndCompleteExceptionally(error: Throwable) {
    ApplicationManager.getApplication().invokeLater {
      completeExceptionally(error)
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region CompletableFuture common integrations with application actions

  @JvmStatic
  @JvmOverloads
  inline fun <T> runInEdtAsync(modalityState: ModalityState? = null, crossinline action: () -> T): CompletableFuture<T> {
    return CompletableFuture.supplyAsync({ action() }, inEdt(modalityState))
  }

  @JvmStatic
  @JvmOverloads
  fun runInEdtAsync(modalityState: ModalityState? = null, action: Runnable): CompletableFuture<Void> {
    return CompletableFuture.runAsync(action, inEdt(modalityState))
  }

  @JvmStatic
  @JvmOverloads
  inline fun <T> runWriteActionAsync(modalityState: ModalityState? = null, crossinline action: () -> T): CompletableFuture<T> {
    return CompletableFuture.supplyAsync({ action() }, inWriteAction(modalityState))
  }

  @JvmStatic
  @JvmOverloads
  fun runWriteActionAsync(modalityState: ModalityState? = null, action: Runnable): CompletableFuture<Void> {
    return CompletableFuture.runAsync(action, inWriteAction(modalityState))
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */
}