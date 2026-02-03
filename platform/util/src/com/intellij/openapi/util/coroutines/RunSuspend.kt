// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.coroutines

import com.intellij.concurrency.currentThreadContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

// Copied from kotlin.coroutines.jvm.internal.RunSuspend.kt

@OptIn(InternalCoroutinesApi::class)
fun <T> runSuspend(block: suspend () -> T): T {
  val currentJob = currentThreadContext()[Job]
  val run = RunSuspend<T>(currentJob)
  block.startCoroutine(run)
  return run.await()
}

private class RunSuspend<T>(val job: Job?) : Continuation<T> {
  override val context: CoroutineContext
    get() = job ?: EmptyCoroutineContext

  var result: Result<T>? = null

  override fun resumeWith(result: Result<T>) = synchronized(this) {
    this.result = result
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
  }

  fun await(): T {
    synchronized(this) {
      var interrupted = false
      while (true) {
        when (val result = this.result) {
          null ->
            try {
              @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
              (this as Object).wait()
            }
            catch (_: InterruptedException) {
              // Suppress exception or token could be lost.
              interrupted = true
            }
          else -> {
            if (interrupted) {
              // Restore "interrupted" flag
              Thread.currentThread().interrupt()
            }
            return result.getOrThrow() // throw up failure
          }
        }
      }
    }
  }
}
