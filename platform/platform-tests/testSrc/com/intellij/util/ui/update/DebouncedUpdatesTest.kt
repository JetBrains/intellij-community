// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ComponentUtil.forceMarkAsShowing
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@TestApplication
@OptIn(DelicateCoroutinesApi::class)
class DebouncedUpdatesTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  private val container = JPanel().also {
    forceMarkAsShowing(it, true)
  }

  @Test
  fun `test debounce behavior restarts timer on each request`() {
    timeoutRunBlocking {
      val executedValues = CopyOnWriteArrayList<Int>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-debounce", 100.milliseconds)
          .restartTimerOnAdd(true)
          .runLatest { value ->
            executedValues.add(value)
          }

        // Queue values with delays shorter than the debounce window - timer should keep restarting
        queue.queue(1)
        delay(50.milliseconds) // Less than 100ms - timer restarts
        queue.queue(2)
        delay(50.milliseconds) // Less than 100ms - timer restarts again
        queue.queue(3)
        
        // Now wait for the debounce delay to complete
        delay(150.milliseconds)

        // With debounce (restartTimerOnAdd = true), only the last value should execute
        assertEquals(listOf(3), executedValues, "Should execute only the last value after inactivity")
        
        // Queue another value and verify it also executes
        queue.queue(4)
        delay(150.milliseconds)
        
        assertEquals(listOf(3, 4), executedValues, "Should execute the second batch")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test throttle behavior starts timer on first item`() {
    timeoutRunBlocking {
      val executedValues = CopyOnWriteArrayList<Int>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-throttle", 100.milliseconds)
          .restartTimerOnAdd(false) // Throttle mode
          .runLatest { value ->
            executedValues.add(value)
          }

        // Queue multiple values quickly
        queue.queue(1)
        queue.queue(2)
        queue.queue(3)
        
        // With throttle mode, it should wait 100ms from first item, then process latest (3)
        delay(150.milliseconds)

        assertEquals(listOf(3), executedValues, "Should execute latest value after fixed interval")
        
        // Queue more values quickly (all within throttle window)
        queue.queue(4)
        delay(30.milliseconds) // Less than 100ms
        queue.queue(5)
        delay(30.milliseconds) // Less than 100ms
        queue.queue(6)
        
        // Wait for throttle interval to complete (100ms from queue(4))
        delay(150.milliseconds)
        
        // Unlike debounce, throttle doesn't restart timer - it processes after 100ms from first item (4)
        // So it should process 6 (the latest when the 100ms expired)
        assertEquals(listOf(3, 6), executedValues, "Should execute latest value at fixed intervals")
        
        // Verify another batch
        queue.queue(7)
        delay(150.milliseconds)
        
        assertEquals(listOf(3, 6, 7), executedValues, "Should execute third batch")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test actions execute sequentially without cancellation`() {
    timeoutRunBlocking {
      val executionOrder = CopyOnWriteArrayList<String>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-sequential", 50.milliseconds)
          .restartTimerOnAdd(false)
          .runLatest { value ->
            executionOrder.add("start-$value")
            delay(200.milliseconds) // Long-running operation
            executionOrder.add("end-$value")
          }

        // Queue multiple values quickly
        queue.queue(1)
        queue.queue(2)
        queue.queue(3)

        // Wait for first batch to be picked up and executed
        delay(100.milliseconds) // Debounce delay + time to start first execution
        
        // At this point, first action should be running
        assertTrue(executionOrder.contains("start-3"), "First batch should have started with latest value (3)")
        
        // Queue more while first is still executing
        queue.queue(4)
        queue.queue(5)

        // Wait for all to complete
        delay(1000.milliseconds)

        // Verify sequential execution: each action completes before next starts
        assertTrue(executionOrder.size >= 4, "Should have at least 2 complete executions: $executionOrder")
        
        // Check that actions don't interleave (no start-X after another start-Y before end-Y)
        var lastStartIndex = -1
        for (i in executionOrder.indices) {
          if (executionOrder[i].startsWith("start-")) {
            // If there was a previous start, ensure it has a corresponding end before this start
            if (lastStartIndex != -1) {
              val lastValue = executionOrder[lastStartIndex].substringAfter("start-")
              val expectedEnd = "end-$lastValue"
              assertTrue(
                executionOrder.subList(lastStartIndex, i).contains(expectedEnd),
                "Action should complete before next starts: $executionOrder"
              )
            }
            lastStartIndex = i
          }
        }
        
        // Verify all started actions completed (no cancellation)
        val starts = executionOrder.count { it.startsWith("start-") }
        val ends = executionOrder.count { it.startsWith("end-") }
        assertEquals(starts, ends, "All started actions should complete without cancellation: $executionOrder")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test cancellation while waiting in debounce delay`() {
    timeoutRunBlocking {
      val executions = AtomicInteger(0)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      val queue = DebouncedUpdates.forScope<Unit>(scope, "test-debounce-cancel", 200.milliseconds)
        .restartTimerOnAdd(true)
        .runLatest {
          executions.incrementAndGet()
        }

      queue.queue(Unit)
      delay(50.milliseconds) // Task is waiting in debounce delay

      scope.cancel()
      delay(200.milliseconds) // Wait past the debounce period

      assertEquals(0, executions.get(), "Task should not execute after cancellation during debounce")
    }
  }

  @Test
  fun `test cancellation while waiting in channel buffer`() {
    timeoutRunBlocking {
      val startedFirst = AtomicInteger(0)
      val startedSecond = AtomicInteger(0)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      val queue = DebouncedUpdates.forScope<Int>(scope, "test-buffer-cancel", 50.milliseconds)
        .runLatest { value ->
          if (value == 1) {
            startedFirst.incrementAndGet()
            // Non-cancellable work to simulate blocking
            Thread.sleep(200)
          }
          else {
            startedSecond.incrementAndGet()
          }
        }

      queue.queue(1) // First task - will run and block
      delay(100.milliseconds) // Let first task start
      queue.queue(2) // Second task - goes to buffer, waiting for first to finish

      scope.cancel() // Cancel while second is waiting
      delay(100.milliseconds)

      assertEquals(1, startedFirst.get(), "First task should have started")
      assertEquals(0, startedSecond.get(), "Second task should not start after cancellation")
    }
  }

  @Test
  fun `test cancellation while task is running`() {
    timeoutRunBlocking {
      val started = AtomicInteger(0)
      val completed = AtomicInteger(0)
      val cancelled = AtomicInteger(0)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      val queue = DebouncedUpdates.forScope<Unit>(scope, "test-running-cancel", 50.milliseconds)
        .runLatest {
          started.incrementAndGet()
          try {
            delay(200.milliseconds) // Suspension point where cancellation will be noticed
            completed.incrementAndGet()
          }
          catch (e: CancellationException) {
            cancelled.incrementAndGet()
            throw e
          }
        }

      queue.queue(Unit)
      delay(100.milliseconds) // Let task start and reach suspension point

      scope.cancel()
      delay(100.milliseconds)

      assertEquals(1, started.get(), "Task should have started")
      assertEquals(1, cancelled.get(), "Task should be cancelled at suspension point")
      assertEquals(0, completed.get(), "Task should not complete")
    }
  }

  @Test
  fun `test queue after scope cancellation logs warning and skips`() {
    timeoutRunBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      var actionExecuted = false

      val queue = DebouncedUpdates.forScope<Int>(scope, "test-cancelled", 50.milliseconds)
        .runLatest {
          actionExecuted = true
        }

      // Cancel the scope
      scope.cancel()
      delay(50.milliseconds) // Give time for cancellation to propagate

      // Try to queue after cancellation - should log warning and skip silently
      val warnings = mutableListOf<String>()
      LoggedErrorProcessor.executeWith<Exception>(object : LoggedErrorProcessor() {
        override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
          warnings.add(message)
          return true
        }
      }, {
        queue.queue(1)
      })

      // Verify warning was logged
      assertTrue(warnings.size == 1, "Expected exactly one warning, got ${warnings.size}")
      assertTrue(warnings[0].contains("cancelled") && warnings[0].contains("test-cancelled"),
                 "Warning should mention cancellation and queue name, got: ${warnings[0]}")

      // Verify action was not executed
      delay(100.milliseconds) // Give time for any potential execution
      assertTrue(!actionExecuted, "Action should not execute after scope cancellation")
    }
  }

  @Test
  fun `test channel holds no values after scope cancellation`() {
    timeoutRunBlocking {
      class QueuedValue(val id: Int) {
        val data = ByteArray(50 * 1024) // 50KB each
        override fun toString() = "QueuedValue($id)"
      }

      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val processedIds = mutableListOf<Int>()

      val queue = DebouncedUpdates.forScope<QueuedValue>(scope, "test-no-retention", 50.milliseconds)
        .runLatest { value ->
          processedIds.add(value.id)
          delay(10.milliseconds)
        }

      // Process several values over time to verify no accumulation
      repeat(5) { i ->
        queue.queue(QueuedValue(i))
        delay(100.milliseconds) // Give enough time to process each one (50ms delay + 10ms action + margin)
      }

      // Should process most or all values (allowing for some timing variance)
      assertTrue(processedIds.size >= 4, "Should process at least 4 values, got: ${processedIds.size}")

      // Cancel scope - this should close the channel and release all values
      scope.cancel()
      delay(100.milliseconds)

      // After scope cancellation, NO values should be retained by the scope
      // (not in the channel, not in flow operators, not in collectLatest)
      LeakHunter.checkLeak(scope, QueuedValue::class.java)
    }
  }

  @Test
  fun `test exception in action does not stop queue`() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val processedValues = CopyOnWriteArrayList<Int>()
    val errorMessage = "Test exception for value 2"

    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      timeoutRunBlocking {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-exception-handling", 50.milliseconds)
          .runLatest { value ->
            if (value == 2) {
              throw RuntimeException(errorMessage)
            }
            processedValues.add(value)
          }

        // Queue multiple values, one of which will throw
        queue.queue(1)
        delay(100.milliseconds) // Wait for first value to process

        queue.queue(2) // This will throw
        delay(100.milliseconds) // Wait for exception to be thrown and logged

        queue.queue(3) // This should still be processed despite previous exception
        delay(100.milliseconds) // Wait for third value to process

        scope.cancel()
      }
    }

    // Verify the exception was logged
    assertTrue(error.message?.contains(errorMessage) == true, "Expected error should have been logged")

    // Verify that values before and after the exception were processed
    assertEquals(listOf(1, 3), processedValues, "Should process values 1 and 3")
  }

  @Test
  fun `test cancelOnDispose cancels queue when disposable is disposed`() {
    timeoutRunBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val executions = AtomicInteger(0)
      val disposable = Disposer.newDisposable()

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-dispose", 100.milliseconds)
          .runLatest {
            executions.incrementAndGet()
          }
          .cancelOnDispose(disposable)

        // Queue first value and let it execute
        queue.queue(1)
        delay(150.milliseconds) // Wait for debounce delay + execution

        assertEquals(1, executions.get(), "First value should have executed")

        // Queue second value but don't let it execute yet
        queue.queue(2)
        delay(25.milliseconds) // Wait a bit but less than the debounce delay (100ms)

        // Dispose the disposable - should cancel the queue while second value is waiting
        Disposer.dispose(disposable)
        delay(150.milliseconds) // Wait past the debounce period

        // Second value should not execute after disposal
        assertEquals(1, executions.get(), "Second value should not execute after disposal")

        // Try to queue after disposal - should log warning and skip silently (verified in other test)
        queue.queue(3)
        delay(100.milliseconds)
        assertEquals(1, executions.get(), "Third value should not execute after disposal")
      }
      finally {
        Disposer.dispose(disposable)
        scope.cancel()
      }
    }
  }

  @Test
  fun `test batched queue with throttle mode collects items during delay window`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<List<Int>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-batched-throttle", 100.milliseconds)
          .runBatched { batch ->
            batches.add(batch)
          }

        delay(40.milliseconds)
        queue.queue(1)
        delay(40.milliseconds)
        queue.queue(2)
        delay(120.milliseconds) // First batch should be emitted: [1, 2] (timer started at queue(1))
        
        queue.queue(3)
        delay(120.milliseconds) // Second batch: [3]
        
        queue.queue(4)
        delay(120.milliseconds) // Third batch: [4]

        assertEquals(3, batches.size, "Should have 3 batches")
        assertEquals(listOf(1, 2), batches[0], "First batch should contain [1, 2]")
        assertEquals(listOf(3), batches[1], "Second batch should contain [3]")
        assertEquals(listOf(4), batches[2], "Third batch should contain [4]")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test batched queue with throttle mode collects all items within window`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<List<Int>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-batched-collect", 100.milliseconds)
          .runBatched { batch ->
            batches.add(batch)
          }

        // Queue items rapidly (all within throttle window)
        queue.queue(1)
        delay(30.milliseconds)
        queue.queue(2)
        delay(30.milliseconds)
        queue.queue(3)
        
        // Timer starts at queue(1), after 100ms it should process [1, 2, 3]
        delay(100.milliseconds)

        assertEquals(1, batches.size, "Should have 1 batch")
        assertEquals(listOf(1, 2, 3), batches[0], "Batch should contain all items within delay window")
        
        // Queue more items after first batch processes
        queue.queue(4)
        delay(30.milliseconds)
        queue.queue(5)
        delay(30.milliseconds)
        queue.queue(6)
        delay(150.milliseconds)
        
        assertEquals(2, batches.size, "Should have 2 batches")
        assertEquals(listOf(4, 5, 6), batches[1], "Second batch should contain [4, 5, 6]")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test batched queue with debounce mode restarts timer on each item`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<List<Int>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-batched-debounce", 100.milliseconds)
          .restartTimerOnAdd(true)
          .runBatched { batch ->
            batches.add(batch)
          }

        // Queue items with delays shorter than debounce window - timer keeps restarting
        queue.queue(1)
        delay(50.milliseconds)
        queue.queue(2)
        delay(50.milliseconds)
        queue.queue(3)
        delay(150.milliseconds) // Only now should batch be emitted: [1, 2, 3]

        assertEquals(1, batches.size, "Should have 1 batch (debounced)")
        assertEquals(listOf(1, 2, 3), batches[0], "Batch should contain all items [1, 2, 3]")
        
        // Queue another batch to verify it works multiple times
        queue.queue(4)
        delay(50.milliseconds)
        queue.queue(5)
        delay(150.milliseconds) // Second batch: [4, 5]
        
        assertEquals(2, batches.size, "Should have 2 batches")
        assertEquals(listOf(4, 5), batches[1], "Second batch should contain [4, 5]")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test batched distinct queue removes duplicates`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<Set<String>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<String>(scope, "test-batched-deduplicating", 100.milliseconds)
          .runBatchedDistinct { batch ->
            batches.add(batch)
          }

        // Queue items with duplicates
        queue.queue("id1")
        queue.queue("id2")
        queue.queue("id1")  // Duplicate
        queue.queue("id3")
        queue.queue("id2")  // Duplicate
        delay(150.milliseconds) // Wait for first batch

        // Should deduplicate: ["id1", "id2", "id3"]
        assertEquals(1, batches.size)
        assertEquals(setOf("id1", "id2", "id3"), batches[0], "Batch should contain deduplicated items")

        // Queue another batch with duplicates
        queue.queue("id4")
        queue.queue("id4")  // Duplicate
        queue.queue("id5")
        delay(150.milliseconds)

        assertEquals(2, batches.size)
        assertEquals(setOf("id4", "id5"), batches[1], "Second batch should also deduplicate")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test component queue executes only when component is showing`() {
    edtTest {
      val component = JLabel()
      val executedValues = CopyOnWriteArrayList<Int>()

      val queue = DebouncedUpdates.forComponent<Int>(component, "test-component", 50.milliseconds)
        .runLatest { value ->
          executedValues.add(value)
        }

      // Queue value when component is not showing - should not execute
      queue.queue(1)
      delay(100.milliseconds)
      assertEquals(emptyList<Int>(), awaitValue(emptyList()) { executedValues.toList() })

      // Show component - execution should start
      withShowingChanged { container.add(component) }
      yield()
      assertEquals(listOf(1), awaitValue(listOf(1)) { executedValues.toList() })

      // Queue another value while showing - should execute
      queue.queue(2)
      delay(100.milliseconds)
      assertEquals(listOf(1, 2), awaitValue(listOf(1, 2)) { executedValues.toList() })

      // Hide component
      withShowingChanged { container.remove(component) }
      yield()

      // Queue value while hidden - should not execute
      queue.queue(3)
      delay(100.milliseconds)
      assertEquals(listOf(1, 2), awaitValue(listOf(1, 2)) { executedValues.toList() })

      // Show component again - queued value should execute
      withShowingChanged { container.add(component) }
      yield()
      assertEquals(listOf(1, 2, 3), awaitValue(listOf(1, 2, 3)) { executedValues.toList() })
    }
  }

  @Test
  fun `test component batched queue collects items only when showing`() {
    edtTest {
      val component = JLabel()
      val batches = CopyOnWriteArrayList<List<Int>>()

      val queue = DebouncedUpdates.forComponent<Int>(component, "test-component-batched", 100.milliseconds)
        .runBatched { batch ->
          batches.add(batch)
        }

      // Queue items when not showing
      queue.queue(1)
      delay(40.milliseconds)
      queue.queue(2)
      delay(120.milliseconds)
      assertEquals(emptyList<List<Int>>(), awaitValue(emptyList()) { batches.toList() })

      // Show component - batched items should execute
      withShowingChanged { container.add(component) }
      yield()
      delay(120.milliseconds)
      assertEquals(1, awaitValue(1) { batches.size })
      assertEquals(listOf(1, 2), awaitValue(listOf(1, 2)) { batches[0] })

      // Queue more items while showing
      queue.queue(3)
      delay(40.milliseconds)
      queue.queue(4)
      delay(120.milliseconds)
      assertEquals(2, awaitValue(2) { batches.size })
      assertEquals(listOf(3, 4), awaitValue(listOf(3, 4)) { batches[1] })
    }
  }

  @Test
  fun `test zero delay processes immediately`() {
    edtTest {
      val executedValues = CopyOnWriteArrayList<Int>()
      val scope = CoroutineScope(SupervisorJob())
      
      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-zero-delay", 0.milliseconds)
          .runLatest { value ->
            executedValues.add(value)
          }

        // Queue a value and measure how quickly it's processed
        val start = TimeSource.Monotonic.markNow()
        queue.queue(1)
        
        // Wait for processing with zero delay - should be very fast
        awaitValue(listOf(1)) { executedValues.toList() }
        val elapsed = start.elapsedNow()
        
        // Should process much faster than typical delays (< 10ms)
        assertTrue(elapsed < 10.milliseconds, "Zero delay should process in < 10ms, took $elapsed")
        
        // Verify the value was processed correctly
        assertEquals(listOf(1), executedValues.toList())
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test component queue with debounce behavior`() {
    edtTest {
      val component = JLabel()
      val executedValues = CopyOnWriteArrayList<Int>()

      withShowingChanged { container.add(component) }
      yield()

      val queue = DebouncedUpdates.forComponent<Int>(component, "test-component-debounce", 100.milliseconds)
        .restartTimerOnAdd(true)
        .runLatest { value ->
          executedValues.add(value)
        }

      // Queue values with delays shorter than debounce window
      queue.queue(1)
      delay(50.milliseconds)
      queue.queue(2)
      delay(50.milliseconds)
      queue.queue(3)
      delay(150.milliseconds)

      // Only last value should execute (debouncing)
      assertEquals(listOf(3), awaitValue(listOf(3)) { executedValues.toList() })
    }
  }

  private suspend fun <T> awaitValue(expected: T, getter: () -> T): T {
    val mark = TimeSource.Monotonic.markNow()
    var value = getter()
    while (mark.elapsedNow() < 5.seconds) {
      if (value == expected) break
      delay(1.milliseconds)
      value = getter()
    }
    return value
  }

  @Test
  fun `test waitForAllExecuted waits for processing to complete`() {
    timeoutRunBlocking {
      val executedValues = CopyOnWriteArrayList<Int>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-wait", 100.milliseconds)
          .runLatest { value ->
            delay(50.milliseconds) // Simulate slow processing
            executedValues.add(value)
          }

        // Queue some items
        queue.queue(1)
        queue.queue(2)
        queue.queue(3)

        // Wait for all to be executed
        withContext(Dispatchers.IO) {
          queue.waitForAllExecuted(2.seconds)
        }

        // All items should be processed (only latest value due to runLatest)
        assertEquals(listOf(3), executedValues.toList())
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test queue inside queue must not interfere with waitForAllExecuted`() {
    timeoutRunBlocking {
      val executedValues = CopyOnWriteArrayList<Int>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        lateinit var queue: UpdateQueue<Int>
        queue = DebouncedUpdates.forScope<Int>(scope, "test-nested-wait", 50.milliseconds)
          .runLatest { value ->
            executedValues.add(value)
            // Queue another item during processing (nested queuing)
            if (value < 5) {
              queue.queue(value + 1)
            }
          }

        // Queue initial item - this will trigger nested queuing
        queue.queue(1)

        // Wait for all to be executed (including nested items)
        withContext(Dispatchers.IO) {
          queue.waitForAllExecuted(5.seconds)
        }

        // Should have processed 1, 2, 3, 4, 5 (nested queuing)
        assertEquals(listOf(1, 2, 3, 4, 5), executedValues.toList())
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test waitForAllExecuted with batched queue`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<List<Int>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-wait-batched", 100.milliseconds)
          .runBatched { batch ->
            delay(30.milliseconds) // Simulate processing
            batches.add(batch)
          }

        // Queue items rapidly
        queue.queue(1)
        queue.queue(2)
        queue.queue(3)

        // Wait for all to be executed
        withContext(Dispatchers.IO) {
          queue.waitForAllExecuted(2.seconds)
        }

        // Should have processed one batch with all items
        assertEquals(1, batches.size)
        assertEquals(listOf(1, 2, 3), batches[0])
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test waitForAllExecuted timeout`() {
    timeoutRunBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-wait-timeout", 100.milliseconds)
          .runLatest { value ->
            // Very slow processing
            delay(10.seconds)
          }

        queue.queue(1)

        // Should timeout - waitForAllExecuted uses runBlockingCancellable which wraps exceptions
        var caughtException: Exception? = null
        try {
          withContext(Dispatchers.IO) {
            queue.waitForAllExecuted(200.milliseconds)
          }
          fail("Expected timeout exception")
        } catch (e: com.intellij.openapi.progress.CeProcessCanceledException) {
          // runBlockingCancellable wraps the TimeoutException
          caughtException = e
        } catch (e: TimeoutException) {
          // In case the implementation changes to not use runBlockingCancellable
          caughtException = e
        }

        assertTrue(caughtException != null, "Expected a timeout exception")
      } finally {
        scope.cancel()
      }
    }
  }

  private fun edtTest(block: suspend CoroutineScope.() -> Unit) {
    timeoutRunBlocking(timeout = 30.seconds) {
      withForcedRespectIsShowingClientProperty {
        withContext(Dispatchers.EDT, block)
      }
    }
  }
}
