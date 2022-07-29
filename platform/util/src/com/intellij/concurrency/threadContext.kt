// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ThreadContext")
@file:Experimental

package com.intellij.concurrency

import com.intellij.openapi.application.AccessToken
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val tlCoroutineContext: ThreadLocal<CoroutineContext?> = ThreadLocal()

@Internal
@VisibleForTesting
fun checkUninitializedThreadContext() {
  check(tlCoroutineContext.get() == null) {
    "Thread context was already set"
  }
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
  return updateThreadContext {
    null
  }
}

/**
 * Replaces the current thread context with [coroutineContext].
 *
 * @return handle to restore the previous thread context
 */
fun replaceThreadContext(coroutineContext: CoroutineContext): AccessToken {
  return updateThreadContext {
    coroutineContext
  }
}

/**
 * Updates the current thread context with [coroutineContext] as per [CoroutineContext.plus],
 * and runs the [action].
 *
 * @return result of [action] invocation
 */
fun <X> withThreadContext(coroutineContext: CoroutineContext, action: () -> X): X {
  return withThreadContext(coroutineContext).use {
    action()
  }
}

/**
 * Updates the current thread context with [coroutineContext] as per [CoroutineContext.plus].
 *
 * @return handle to restore the previous thread context
 */
fun withThreadContext(coroutineContext: CoroutineContext): AccessToken {
  return updateThreadContext { current ->
    if (current == null) {
      coroutineContext
    }
    else {
      current + coroutineContext
    }
  }
}

private fun updateThreadContext(
  update: (CoroutineContext?) -> CoroutineContext?
): AccessToken {
  val previousContext = tlCoroutineContext.get()
  val newContext = update(previousContext)
  tlCoroutineContext.set(newContext)
  return object : AccessToken() {
    override fun finish() {
      val currentContext = tlCoroutineContext.get()
      tlCoroutineContext.set(previousContext)
      check(currentContext === newContext) {
        "Context was not reset correctly"
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
 *   replaceThreadContext(context).use {
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
  return ContextRunnable(true, runnable)
}

/**
 * Same as [captureThreadContext] but for [Callable].
 */
fun <V> captureThreadContext(callable: Callable<V>): Callable<V> {
  return ContextCallable(true, callable)
}
