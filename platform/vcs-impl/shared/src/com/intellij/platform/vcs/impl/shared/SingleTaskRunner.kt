// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
  private val delay: Duration = Duration.Companion.ZERO,
  private val task: suspend () -> Unit,
) {
  private val requested = MutableStateFlow(false)
  private val busy = MutableStateFlow(false)

  private val runNow = Channel<Unit>(capacity = Channel.CONFLATED)

  fun getPendingTasksFlow(): Flow<Boolean> = requested.combine(busy) { requested, busy -> !requested && !busy }

  private val processorJob = cs.launch(Dispatchers.Default, CoroutineStart.LAZY) {
    try {
      while (true) {
        checkCanceled()
        requested.first { it }
        if (delay.isPositive()) {
          withTimeoutOrNull(delay) { runNow.receive() }
        }
        busy.value = true
        requested.value = false
        checkCanceled()
        runCatching {
          task()
        }.getOrHandleException { LOG.error("Task failed", it) }
        busy.value = false
      }
    }
    finally {
      requested.value = false
      busy.value = false
      LOG.debug { "Task processing finished" }
    }
  }

  fun request() {
    if (processorJob.isCancelled) return
    requested.value = true
  }

  fun requestNow() {
    if (processorJob.isCancelled) return
    runNow.trySend(Unit)
    requested.value = true
  }

  fun start() {
    LOG.debug { "Task processing started" }
    processorJob.start()
  }

  /**
   * Await the state where the task is not executed and there are no requests to do so.
   */
  suspend fun awaitNotBusy() {
    getPendingTasksFlow().first { it }
  }

  companion object {
    private val LOG = logger<SingleTaskRunner>()
  }
}