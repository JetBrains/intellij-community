// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

/**
 * Just use `SharedFlow<Unit>.collect { task() }` if you don't need [awaitNotBusy]
 * Runs at most one task at a time.
 * Task execution will not be launched until [start] is called.
 */
internal class SingleTaskRunner(
  cs: CoroutineScope,
  private val task: suspend () -> Unit,
) {
  private val requested = MutableStateFlow(false)
  private val busy = MutableStateFlow(false)

  private val processorJob = cs.launch(Dispatchers.Default, CoroutineStart.LAZY) {
    try {
      while (true) {
        checkCanceled()
        requested.first { it }
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
    }
  }

  fun request() {
    if (processorJob.isCancelled) return
    requested.value = true
  }

  fun start() {
    processorJob.start()
  }

  /**
   * Await the state where the task is not executed and there are no requests to do so.
   */
  suspend fun awaitNotBusy() {
    requested.combine(busy) { requested, busy -> !requested && !busy }.first { it }
  }
}

@Suppress("FunctionName")
internal fun DelayedTaskRunner(
  cs: CoroutineScope,
  delay: Duration,
  task: suspend () -> Unit,
): SingleTaskRunner =
  SingleTaskRunner(cs) {
    delay(delay)
    task()
  }