// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.platform.util.coroutines.flow.collectLatestCoalesced
import com.intellij.platform.util.coroutines.flow.collectLatestCoalescedIn
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FlowTest {

  @Test
  fun `stalled processing for one key does not block another key`(): Unit = timeoutRunBlocking {
    val flow = MutableSharedFlow<Pair<Int, String>>()

    val key1Started = CompletableDeferred<Unit>()
    val key1Continue = CompletableDeferred<Unit>()
    val key2Started = CompletableDeferred<Unit>()
    val key2Finished = CompletableDeferred<Unit>()

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this, Pair<Int, String>::first) { (key, _) ->
        when (key) {
          1 -> {
            key1Started.complete(Unit)
            // Stall key 1 until we decide to continue
            key1Continue.await()
          }
          2 -> {
            key2Started.complete(Unit)
            // Simulate short processing and then mark finished
            delay(20)
            key2Finished.complete(Unit)
          }
        }
      }
    }

    // Start processing for key 1 and keep it stalled
    flow.emit(1 to "a")
    key1Started.await()

    // While key 1 is stalled, start processing for key 2; it should start and finish independently
    flow.emit(2 to "b")
    key2Started.await()
    key2Finished.await()

    // Unblock key 1 and cleanup
    key1Continue.complete(Unit)

    job.cancel()
  }

  @Test
  fun `only one block per key executes at a time`(): Unit = timeoutRunBlocking {
    val flow = MutableSharedFlow<Pair<Int, String>>()

    // Synchronization primitives to detect overlap and ordering
    val firstStarted = CompletableDeferred<Unit>()
    val firstCancelled = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()

    // This flag will be set if both handlers for the same key are active simultaneously
    val overlapDetected = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this, Pair<Int, String>::first) { (key, value) ->
        if (key != 1) return@collectLatestCoalescedIn
        if (!firstStarted.isCompleted) {
          // First run for key=1
          firstStarted.complete(Unit)
          try {
            // Stay active until cancelled by the second emission
            // Periodically yield and check for overlap
            while (isActive) {
              delay(10)
            }
          }
          finally {
            firstCancelled.complete(Unit)
          }
        }
        else {
          // Second run for key=1 must not start until the first is cancelled
          // If it starts while first hasn't been cancelled yet, that's an overlap
          if (!firstCancelled.isCompleted) {
            overlapDetected.incrementAndGet()
          }
          secondStarted.complete(Unit)
          // Short work
          delay(20)
        }
      }
    }

    // Emit first value for key=1 and wait until it starts
    flow.emit(1 to "a")
    firstStarted.await()

    // Emit second value for the same key; should cancel the first, and the second should start only after cancellation completes
    flow.emit(1 to "b")

    // Wait for the first to signal cancellation, then the second to start
    firstCancelled.await()
    secondStarted.await()

    // Verify no overlap occurred
    assertEquals(0, overlapDetected.get(), "Only one block per key should execute at any moment in time")

    job.cancel()
  }

  @Test
  fun `cancels previous job for the same key`(): Unit = timeoutRunBlocking {
    val flow = MutableSharedFlow<Pair<Int, String>>()
    val started = AtomicInteger(0)
    val cancelled = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatestCoalescedIn(this, Pair<Int, String>::first) { v ->
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
    yield()
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
      flow.collectLatestCoalescedIn(this, Pair<Int, String>::first) { (key, _) ->
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
      flow.collectLatestCoalescedIn(supervisorScope, Pair<Int, String>::first) { (key, _) ->
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

    val job = launch(start = CoroutineStart.UNDISPATCHED, context = Dispatchers.Default) {
      flow.collectLatestCoalescedIn(this, Pair<Int, Int>::first) { v ->
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

    assertEquals(1, finished.get(), "Only the last emission's task should finish for the same key")

    job.cancel()
  }

  @Test
  fun `overload works`(): Unit = timeoutRunBlocking {
    val started = AtomicInteger(0)
    val finished = AtomicInteger(0)

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      flow {
        emit(1)
        delay(10)
        emit(2)
        delay(10)
        emit(1)
      }.collectLatestCoalesced { _ ->
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