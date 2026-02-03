// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.core.ObservableOperationStatus.*
import com.intellij.openapi.observable.operation.core.*
import com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class AtomicOperationTraceTest : AtomicOperationTraceTestCase() {

  @Test
  fun `test simple operation`() {
    val trace = AtomicOperationTrace()

    assertOperationState(trace, COMPLETED)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)

    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)
  }

  @Test
  fun `test compound operation`() {
    val trace = AtomicOperationTrace()

    assertOperationState(trace, COMPLETED)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)

    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)

    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)

    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)
  }

  @Test
  fun `test unidentified operation`() {
    val trace = AtomicOperationTrace()

    assertOperationState(trace, COMPLETED)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish()
    assertOperationState(trace, COMPLETED)
  }

  @Test
  fun `test operation scheduling`() {
    val trace = AtomicOperationTrace()
    val task1 = OperationExecutionId.createId("task1")
    val task2 = OperationExecutionId.createId("task2")

    assertOperationState(trace, COMPLETED)
    trace.traceSchedule(task2)
    assertOperationState(trace, SCHEDULED)
    trace.traceStart(task1)
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish(task1)
    assertOperationState(trace, IN_PROGRESS)
    trace.traceStart(task2)
    assertOperationState(trace, IN_PROGRESS)
    trace.traceFinish(task2)
    assertOperationState(trace, COMPLETED)
  }

  @Test
  fun `test merging operation trace`() {
    val trace = AtomicOperationTrace(isMerging = true)
    val task1 = OperationExecutionId.createId("task1")
    val task2 = OperationExecutionId.createId("task2")

    assertOperationState(trace, COMPLETED)
    trace.traceSchedule(task1)
    assertOperationState(trace, SCHEDULED)
    trace.traceSchedule(task1)
    assertOperationState(trace, SCHEDULED)
    trace.traceRun(task1) {
      assertOperationState(trace, IN_PROGRESS)
    }
    assertOperationState(trace, COMPLETED)

    trace.traceSchedule(task1)
    assertOperationState(trace, SCHEDULED)
    trace.traceSchedule(task2)
    assertOperationState(trace, SCHEDULED)
    trace.traceRun(task1) {
      assertOperationState(trace, IN_PROGRESS)
    }
    assertOperationState(trace, IN_PROGRESS)
    trace.traceRun(task2) {
      assertOperationState(trace, IN_PROGRESS)
    }
    assertOperationState(trace, COMPLETED)
  }

  @Test
  fun `test parallel execution`() {
    val trace = AtomicOperationTrace()

    repeat(1000) {
      val latch = CountDownLatch(1)
      val startedOperations = AtomicInteger(0)
      val completedOperations = AtomicInteger(0)
      trace.whenOperationStarted {
        startedOperations.incrementAndGet()
      }
      trace.whenOperationFinished {
        completedOperations.incrementAndGet()
      }

      assertOperationState(trace, COMPLETED)
      repeat(10) { trace.traceStart() }
      assertOperationState(trace, IN_PROGRESS)
      val threads = generate(10) {
        thread {
          latch.await()
          trace.traceFinish()
        }
      }
      latch.countDown()
      threads.forEach(Thread::join)
      assertOperationState(trace, COMPLETED)

      assertEquals(1, startedOperations.get())
      assertEquals(1, completedOperations.get())
    }
  }

  @Test
  fun `test super compound parallel execution`() {
    val trace = AtomicOperationTrace()
    repeat(1000) {
      val latch = CountDownLatch(1)
      val startedOperations = AtomicInteger(0)
      val completedOperations = AtomicInteger(0)
      trace.whenOperationStarted {
        startedOperations.incrementAndGet()
      }
      trace.whenOperationFinished {
        completedOperations.incrementAndGet()
      }

      val threads = generate(10) {
        thread {
          latch.await()
          trace.traceStart()
        }
      }
      latch.countDown()
      threads.forEach(Thread::join)
      repeat(10) { trace.traceFinish() }
      assertOperationState(trace, COMPLETED)

      assertEquals(1, startedOperations.get())
      assertEquals(1, completedOperations.get())
    }
  }

  @Test
  fun `test operation listener start with TTL`() {
    val trace = AtomicOperationTrace()
    val isStarted = AtomicBoolean(false)
    val isFinished = AtomicBoolean(false)
    trace.onceWhenOperationStarted {
      isStarted.set(true)
    }
    trace.onceWhenOperationFinished {
      isFinished.set(true)
    }

    trace.traceStart()
    trace.traceFinish()

    assertTrue(isStarted.get())
    assertTrue(isFinished.get())
  }

  @Test
  fun `test operation listening with TTL`() {
    val trace = AtomicOperationTrace()
    val ttl = 10
    val unsubscribeIndex = 7

    val startCounter = AtomicInteger(0)
    val finishCounter = AtomicInteger(0)
    val startDisposable = Disposer.newDisposable(testDisposable, "Start operation subscription")
    trace.whenOperationStarted(ttl, startDisposable) {
      startCounter.incrementAndGet()
    }
    trace.whenOperationFinished(ttl) {
      finishCounter.incrementAndGet()
    }

    repeat(ttl) { index ->
      if (index == unsubscribeIndex) {
        Disposer.dispose(startDisposable)
      }
      trace.traceStart()
      trace.traceFinish()
    }
    trace.traceStart()
    trace.traceFinish()

    assertEquals(unsubscribeIndex, startCounter.get())
    assertEquals(ttl, finishCounter.get())
  }
}