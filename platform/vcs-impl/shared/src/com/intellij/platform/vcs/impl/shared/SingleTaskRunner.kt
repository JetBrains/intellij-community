// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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

  private val runNow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val processorJob = cs.launch(Dispatchers.Default, CoroutineStart.LAZY) {
    try {
      while (true) {
        checkCanceled()
        requested.first { it }
        if (delay.isPositive()) {
          withTimeoutOrNull(delay) { runNow.firstOrNull() }
        }
        busy.value = true
        requested.value = false
        checkCanceled()
        task()
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
    runNow.tryEmit(Unit)
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
    requested.combine(busy) { requested, busy -> !requested && !busy }.first { it }
  }

  companion object {
    private val LOG = logger<SingleTaskRunner>()
  }
}