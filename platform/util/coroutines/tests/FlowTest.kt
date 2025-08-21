// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.platform.util.coroutines.flow.collectLatestCoalescedIn
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FlowTest {

  @Test
  fun `cancels previous job for the same key`(): Unit = timeoutRunBlocking {
    val flow = MutableSharedFlow<Pair<Int, String>>()
    val started = AtomicInteger(0)
    val cancelled = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this) { _, _ ->
        started.incrementAndGet()
        try {
          // Long running to ensure it's active when a new value for the same key arrives
          delay(10_000)
        }
        finally {
          // If cancelled, we record it (both cancellations and normal completion pass here)
          cancelled.incrementAndGet()
        }
      }
    }

    flow.emit(1 to "a")
    // Give the first one a chance to start
    delay(50)
    flow.emit(1 to "b")

    // Wait a bit for cancellation to happen
    repeat(50) {
      if (cancelled.get() >= 1) return@repeat
      delay(10)
    }
    assertEquals(2, started.get(), "Two tasks should have started for the same key")
    assertTrue(cancelled.get() >= 1, "The first task should be cancelled when the second starts")

    job.cancel()
  }

  @Test
  fun `independent keys run concurrently`(): Unit = timeoutRunBlocking {
    val start1 = CompletableDeferred<Unit>()
    val start2 = CompletableDeferred<Unit>()
    val complete1 = CompletableDeferred<Unit>()
    val complete2 = CompletableDeferred<Unit>()

    val flow = MutableSharedFlow<Pair<Int, String>>()

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this) { key, _ ->
        if (key == 1) {
          start1.complete(Unit)
          complete1.await()
        }
        else if (key == 2) {
          start2.complete(Unit)
          complete2.await()
        }
      }
    }

    launch { flow.emit(1 to "a") }
    launch { flow.emit(2 to "b") }

    // Ensure both started without waiting for each other
    start1.await()
    start2.await()

    // Now allow them to complete in arbitrary order
    complete1.complete(Unit)
    complete2.complete(Unit)

    job.cancel()
  }

  @Test
  fun `exception in one key does not cancel others when using Supervisor scope`(): Unit = timeoutRunBlocking {
    val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> /* ignore */ }
    @Suppress("RAW_SCOPE_CREATION") val supervisorScope = CoroutineScope(SupervisorJob() + handler)
    val flow = MutableSharedFlow<Pair<Int, String>>()

    val finished2 = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(supervisorScope) { key, _ ->
        if (key == 1) {
          throw IllegalStateException("boom")
        }
        else if (key == 2) {
          delay(100)
          finished2.incrementAndGet()
        }
      }
    }

    flow.emit(1 to "x")
    flow.emit(2 to "y")

    // Give time for key 2 to finish even though key 1 fails
    delay(200)
    assertEquals(1, finished2.get(), "Key 2 should complete despite exception in key 1 when using Supervisor scope")

    job.cancel()
  }

  @Test
  fun `rapid emissions coalesce per key`(): Unit = timeoutRunBlocking {
    val flow = MutableSharedFlow<Pair<Int, Int>>()
    val started = AtomicInteger(0)
    val finished = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this) { _, _ ->
        started.incrementAndGet()
        // Long enough to ensure subsequent emissions arrive before completion
        delay(50)
        finished.incrementAndGet()
      }
    }

    // Rapidly emit multiple values for the same key
    repeat(5) { i -> flow.emit(1 to i) }

    // Allow processing to settle
    delay(200)

    assertEquals(5, started.get(), "Each emission should start a new task for the same key")
    assertEquals(1, finished.get(), "Only the last emission's task should finish for the same key")

    job.cancel()
  }

  @Test
  fun `key-only overload works`(): Unit = timeoutRunBlocking {
    val started = AtomicInteger(0)
    val finished = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow {
        emit(1)
        delay(10)
        emit(2)
        delay(10)
        emit(1)
      }.collectLatestCoalescedIn(this) { _ ->
        started.incrementAndGet()
        delay(50)
        finished.incrementAndGet()
      }
    }

    delay(300)

    assertEquals(3, started.get())
    assertEquals(2, finished.get(), "Second emission for key 1 cancels the first one")

    job.cancel()
  }
}