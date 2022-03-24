// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ThreadContext")
@file:Experimental

package com.intellij.concurrency

import com.intellij.openapi.application.AccessToken
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val tlCoroutineContext: ThreadLocal<CoroutineContext?> = ThreadLocal()

internal fun checkUninitializedThreadContext() {
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
 * Replaces the current thread context with [coroutineContext].
 *
 * @return handle to restore the previous thread context
 */
fun resetThreadContext(coroutineContext: CoroutineContext): AccessToken {
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
    current + coroutineContext
  }
}

private fun updateThreadContext(
  update: (CoroutineContext) -> CoroutineContext
): AccessToken {
  val previousContext = tlCoroutineContext.get()
  val newContext = update(previousContext ?: EmptyCoroutineContext)
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
