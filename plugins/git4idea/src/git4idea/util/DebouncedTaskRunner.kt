// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration

/**
 * A suspending version of [com.intellij.util.SingleAlarm] with a constant debounce and without compatibility for 20y/o codebase.
 *
 * Task execution will not be launched until [start] is called.
 */
internal class DebouncedTaskRunner @VisibleForTesting constructor(
  cs: CoroutineScope,
  private val awaitDebounce: suspend () -> Unit,
  private val task: suspend () -> Unit,
) {
  constructor(
    cs: CoroutineScope,
    debounce: Duration,
    task: suspend () -> Unit,
  ) : this(cs, { delay(debounce) }, task)

  private val requests = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  private val busyState = MutableStateFlow(false)

  private val processorJob = cs.launch(Dispatchers.Default, CoroutineStart.LAZY) {
    try {
      while (true) {
        if (!checkWasRequested()) {
          requests.receive()
        }
        awaitDebounce()
        requests.tryReceive() // drop requests that arrived during the debounce
        task()
      }
    }
    finally {
      shutDown()
    }
  }

  /**
   * Synchronously checks if a request was submitted and resets the busy state if there's none.
   *
   * @return true if a request was submitted, false if not.
   */
  @Synchronized
  private fun checkWasRequested(): Boolean {
    val received = requests.tryReceive()
    if (received.isFailure) {
      busyState.value = false
      return false
    }
    else {
      return true
    }
  }

  @Synchronized
  fun request() {
    if (requests.trySend(Unit).isFailure) return
    busyState.value = true
  }

  @Synchronized
  private fun shutDown() {
    requests.close()
    busyState.value = false
  }

  fun start() {
    processorJob.start()
  }

  /**
   * Await the state where the task is not executed and there are no requests to do so.
   */
  suspend fun awaitNotBusy() {
    busyState.first { !it }
  }
}