// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@OptIn(DelicateCoroutinesApi::class)
class DebouncedUpdatesTest {

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
        delay(50) // Less than 100ms - timer restarts
        queue.queue(2)
        delay(50) // Less than 100ms - timer restarts again
        queue.queue(3)
        
        // Now wait for the debounce delay to complete
        delay(150)

        // With debounce (restartTimerOnAdd = true), only the last value should execute
        assertEquals(listOf(3), executedValues, "Should execute only the last value after inactivity")
        
        // Queue another value and verify it also executes
        queue.queue(4)
        delay(150)
        
        assertEquals(listOf(3, 4), executedValues, "Should execute the second batch")
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `test collectLatest cancellation on new value`() {
    timeoutRunBlocking {
      val startedExecutions = AtomicInteger(0)
      val completedExecutions = AtomicInteger(0)
      val cancelledExecutions = AtomicInteger(0)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-cancel", 50.milliseconds)
          .restartTimerOnAdd(false)
          .runLatest { value ->
            startedExecutions.incrementAndGet()
            try {
              delay(200) // Long-running operation
              completedExecutions.incrementAndGet()
            }
            catch (e: CancellationException) {
              cancelledExecutions.incrementAndGet()
              throw e
            }
          }

        // Queue first value
        queue.queue(1)
        delay(100) // Wait for it to start executing

        assertTrue(startedExecutions.get() >= 1, "First execution should have started")

        // Queue second value while first is still running - should cancel first
        queue.queue(2)
        delay(100) // Wait for second to start

        // Wait for second to complete
        delay(200)

        assertEquals(2, startedExecutions.get(), "Should start 2 executions")
        assertEquals(1, cancelledExecutions.get(), "First execution should be cancelled")
        assertEquals(1, completedExecutions.get(), "Only second execution should complete")
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
      delay(50) // Task is waiting in debounce delay

      scope.cancel()
      delay(200) // Wait past the debounce period

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
      delay(100) // Let first task start
      queue.queue(2) // Second task - goes to buffer, waiting for first to finish

      scope.cancel() // Cancel while second is waiting
      delay(100)

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
            delay(200) // Suspension point where cancellation will be noticed
            completed.incrementAndGet()
          }
          catch (e: CancellationException) {
            cancelled.incrementAndGet()
            throw e
          }
        }

      queue.queue(Unit)
      delay(100) // Let task start and reach suspension point

      scope.cancel()
      delay(100)

      assertEquals(1, started.get(), "Task should have started")
      assertEquals(1, cancelled.get(), "Task should be cancelled at suspension point")
      assertEquals(0, completed.get(), "Task should not complete")
    }
  }

  @Test
  fun `test queue after scope cancellation throws`() {
    timeoutRunBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      val queue = DebouncedUpdates.forScope<Int>(scope, "test-throw", 50.milliseconds)
        .runLatest {
          // Should never execute
          fail("Should not execute after scope cancellation")
        }

      // Cancel the scope
      scope.cancel()
      delay(50) // Give time for cancellation to propagate

      // Try to queue after cancellation - should throw
      try {
        queue.queue(1)
        fail("Expected IllegalArgumentException to be thrown")
      }
      catch (e: IllegalArgumentException) {
        assertTrue(e.message?.contains("cancelled") == true, "Exception message should mention cancellation")
      }
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
          delay(10)
        }

      // Process several values over time to verify no accumulation
      repeat(5) { i ->
        queue.queue(QueuedValue(i))
        delay(60) // Give time to process each one
      }

      assertEquals(5, processedIds.size, "Should process 5 values")

      // Cancel scope - this should close the channel and release all values
      scope.cancel()
      delay(100)

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
        delay(100) // Wait for first value to process

        queue.queue(2) // This will throw
        delay(100) // Wait for exception to be thrown and logged

        queue.queue(3) // This should still be processed despite previous exception
        delay(100) // Wait for third value to process

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
        delay(150) // Wait for debounce delay + execution

        assertEquals(1, executions.get(), "First value should have executed")

        // Queue second value but don't let it execute yet
        queue.queue(2)
        delay(25) // Wait a bit but less than the debounce delay (100ms)

        // Dispose the disposable - should cancel the queue while second value is waiting
        Disposer.dispose(disposable)
        delay(150) // Wait past the debounce period

        // Second value should not execute after disposal
        assertEquals(1, executions.get(), "Second value should not execute after disposal")

        // Try to queue after disposal - should throw
        try {
          queue.queue(3)
          fail("Expected IllegalArgumentException to be thrown after disposal")
        }
        catch (e: IllegalArgumentException) {
          assertTrue(e.message?.contains("cancelled") == true, "Exception should mention cancellation")
        }
      }
      finally {
        Disposer.dispose(disposable)
        scope.cancel()
      }
    }
  }

  @Test
  fun `test batched queue collects items during delay window`() {
    timeoutRunBlocking {
      val batches = CopyOnWriteArrayList<List<Int>>()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

      try {
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-batched", 100.milliseconds)
          .runBatched { batch ->
            batches.add(batch)
          }

        delay(40)
        queue.queue(1)
        delay(40)
        queue.queue(2)
        delay(120) // First batch should be emitted: [1, 2]
        
        queue.queue(3)
        delay(120) // Second batch: [3]
        
        queue.queue(4)
        delay(120) // Third batch: [4]

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
}
