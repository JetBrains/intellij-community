// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import kotlinx.coroutines.*
import java.util.concurrent.Callable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/* Support for AsyncTaskBenchmark: run java [Callable] via coroutines dispatcher */

@OptIn(DelicateCoroutinesApi::class)
fun <V> runTaskAsyncViaCoroutines(task: Callable<V>, coroutineDispatcher: CoroutineDispatcher): V {
  @Suppress("SSBasedInspection")
  val deferred = CoroutineScope(coroutineDispatcher + CoroutineName("detachedComputation: $task")).async {
    task.call()
  }

  return runSuspend {
    deferred.await()
  }
}

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

  val lock = ReentrantLock()
  val condition = lock.newCondition()

  override fun resumeWith(result: Result<T>) = lock.withLock {
    this.result = result
    condition.signalAll()
  }

  fun await(): T {
    lock.withLock {
      while (true) {
        when (val result = this.result) {
          null -> condition.await()
          else -> {
            return result.getOrThrow() // throw up failure
          }
        }
      }
    }
  }
}
