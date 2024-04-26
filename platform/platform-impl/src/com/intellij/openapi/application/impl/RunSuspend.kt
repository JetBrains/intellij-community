// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

// Copied from kotlin.coroutines.jvm.internal.RunSuspend.kt

internal fun <T> runSuspend(block: suspend () -> T): T {
  val run = RunSuspend<T>()
  block.startCoroutine(run)
  return run.await()
}

private class RunSuspend<T> : Continuation<T> {
  override val context: CoroutineContext
    get() = EmptyCoroutineContext

  var result: Result<T>? = null

  override fun resumeWith(result: Result<T>) = synchronized(this) {
    this.result = result
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
  }

  fun await(): T {
    synchronized(this) {
      while (true) {
        when (val result = this.result) {
          null -> @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).wait()
          else -> {
            return result.getOrThrow() // throw up failure
          }
        }
      }
    }
  }
}
