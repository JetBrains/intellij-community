// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

/**
 * Just use `SharedFlow<Unit>.collect { task() }` if you don't need [awaitNotBusy]
 * Runs at most one task at a time.
 * Task execution will not be launched until [start] is called.
 */
@ApiStatus.Internal
class SingleTaskRunner(
  cs: CoroutineScope,
  private val task: suspend () -> Unit,
) {
  /**
   * Represents the state of the runner:
   * 0 - idle,
   * 1 - a single task is either queued or running,
   * 2 - has one running task and one queued task.
   */
  private val requestCounter = MutableStateFlow(0)

  fun getIdleFlow(): Flow<Boolean> = requestCounter.map { it == 0 }

  private val processorJob = cs.launch(Dispatchers.Default, CoroutineStart.LAZY) {
    try {
      while (true) {
        checkCanceled()
        requestCounter.first { it > 0 }
        checkCanceled()
        runCatching {
          task()
        }.getOrHandleException { LOG.error("Task failed", it) }
        requestCounter.update { it.dec() }
      }
    }
    finally {
      requestCounter.value = 0
      LOG.debug { "Task processing finished" }
    }
  }

  fun request() {
    if (processorJob.isCancelled) return
    requestCounter.update { it.inc().coerceAtMost(2) }
  }

  fun start() {
    LOG.debug { "Task processing started" }
    processorJob.start()
  }

  /**
   * Await the state where the task is not executed and there are no requests to do so.
   */
  suspend fun awaitNotBusy() {
    getIdleFlow().first { it }
  }

  companion object {
    private val LOG = logger<SingleTaskRunner>()

    fun delayedTaskRunner(cs: CoroutineScope, delay: Duration, task: suspend () -> Unit): SingleTaskRunner = SingleTaskRunner(cs) {
      delay(delay)
      task()
    }
  }
}