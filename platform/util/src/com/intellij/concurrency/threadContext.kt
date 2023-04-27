// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ThreadContext")
@file:Experimental

package com.intellij.concurrency

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.captureCallableThreadContext
import com.intellij.util.concurrency.captureRunnableThreadContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG: Logger = Logger.getInstance("#com.intellij.concurrency")

private val tlCoroutineContext: ThreadLocal<CoroutineContext?> = ThreadLocal()

@VisibleForTesting
fun currentThreadContextOrNull(): CoroutineContext? {
  return tlCoroutineContext.get()
}

/**
 * @return current thread context
 */
fun currentThreadContext(): CoroutineContext {
  return tlCoroutineContext.get() ?: EmptyCoroutineContext
}

/**
 * Resets the current thread context to initial value.
 *
 * @return handle to restore the previous thread context
 */
fun resetThreadContext(): AccessToken {
  return withThreadLocal(tlCoroutineContext) { _ ->
    null
  }
}

/**
 * Installs [coroutineContext] as the current thread context.
 * If [replace] is `false` (default) and the current thread already has context, then this function logs an error.
 *
 * @return handle to restore the previous thread context
 */
fun installThreadContext(coroutineContext: CoroutineContext, replace: Boolean = false): AccessToken {
  return withThreadLocal(tlCoroutineContext) { previousContext: CoroutineContext? ->
    if (!replace && previousContext != null) {
      LOG.error("Thread context was already set: $previousContext")
    }
    coroutineContext
  }
}

/**
 * Updates given [variable] with a new value obtained by applying [update] to the current value.
 * Returns a token which must be [closed][AccessToken.close] to revert the [variable] to the previous value.
 * The token implementation ensures that nested updates and reverts are mirrored:
 * [update0, update1, ... updateN, revertN, ... revert1, revert0].
 * Unordered updates, such as [update0, update1, revert0, revert1] will result in [IllegalStateException].
 *
 * Example usage:
 * ```
 * withThreadLocal(ourCounter) { value ->
 *   value + 1
 * }.use {
 *   ...
 * }
 *
 * // or, if the new value does not depend on the current one
 * withThreadLocal(ourCounter) { _ ->
 *   42
 * }.use {
 *   ...
 * }
 * ```
 *
 * TODO ? move to more appropriate package before removing `@Internal`
 */
@Internal
fun <T> withThreadLocal(variable: ThreadLocal<T>, update: (value: T) -> T): AccessToken {
  val previousValue = variable.get()
  val newValue = update(previousValue)
  if (newValue === previousValue) {
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }
  variable.set(newValue)
  return object : AccessToken() {
    override fun finish() {
      val currentValue = variable.get()
      variable.set(previousValue)
      check(currentValue === newValue) {
        "Value was not reset correctly. Expected: $newValue, actual: $currentValue"
      }
    }
  }
}

/**
 * Returns a `Runnable` instance, which saves [currentThreadContext] and,
 * when run, installs the saved context and runs original [runnable] within the installed context.
 * ```
 * val executor = Executors.newSingleThreadExecutor()
 * val context = currentThreadContext()
 * executor.submit {
 *   installThreadContext(context).use {
 *     runnable.run()
 *   }
 * }
 * // is roughly equivalent to
 * executor.submit(captureThreadContext(runnable))
 * ```
 *
 * Before installing the saved context, the returned `Runnable` asserts that there is no context already installed in the thread.
 * This check effectively forbids double capturing, e.g. `captureThreadContext(captureThreadContext(runnable))` will fail.
 * This method should be used with executors from [java.util.concurrent.Executors] or with [java.util.concurrent.CompletionStage] methods.
 * Do not use this method with executors returned from [com.intellij.util.concurrency.AppExecutorUtil], they already capture the context.
 */
fun captureThreadContext(runnable: Runnable): Runnable {
  return captureRunnableThreadContext(runnable)
}

/**
 * Same as [captureCallableThreadContext] but for [Callable].
 */
fun <V> captureThreadContext(callable: Callable<V>): Callable<V> {
  return captureCallableThreadContext(callable)
}
