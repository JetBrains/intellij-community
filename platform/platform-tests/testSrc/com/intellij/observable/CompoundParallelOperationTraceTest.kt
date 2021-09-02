// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.operations.*
import com.intellij.openapi.util.Disposer
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CompoundParallelOperationTraceTest : CompoundParallelOperationTraceTestCase() {

  @Test
  fun `test simple operation`() = testTrace<Int> {
    operation {
      trace.startTask(1)
      trace.finishTask(1)
    }
    operation {
      trace.startTask(1)
      trace.finishTask(1)
    }
  }

  @Test
  fun `test compound operation`() = testTrace<Int> {
    trace.finishTask(0)
    operation {
      trace.startTask(1)
      trace.startTask(2)
      trace.finishTask(2)
      trace.finishTask(1)
    }
    operation {
      trace.startTask(1)
      trace.startTask(2)
      trace.finishTask(1)
      trace.startTask(3)
      trace.finishTask(3)
      trace.finishTask(2)
    }
    operation {
      trace.startTask(1)
      trace.startTask(2)
      trace.startTask(3)
      trace.finishTask(3)
      trace.finishTask(1)
      trace.finishTask(2)
    }
  }

  @Test
  fun `test unidentified operation`() = testTrace<Nothing?> {
    operation {
      trace.startTask(null)
      trace.startTask(null)
      trace.finishTask(null)
      trace.startTask(null)
      trace.finishTask(null)
      trace.finishTask(null)
    }
  }

  @Test
  fun `test parallel execution`() {
    val trace = CompoundParallelOperationTrace<Int>()
    repeat(1000) {
      val latch = CountDownLatch(1)
      val startedOperations = AtomicInteger(0)
      val completedOperations = AtomicInteger(0)
      trace.beforeOperation {
        startedOperations.incrementAndGet()
      }
      trace.afterOperation {
        completedOperations.incrementAndGet()
      }

      assertTrue(trace.isOperationCompleted())
      generate(10, trace::startTask)
      assertFalse(trace.isOperationCompleted())
      val threads = generate(10) {
        thread {
          latch.await()
          trace.finishTask(it)
        }
      }
      latch.countDown()
      threads.forEach(Thread::join)
      assertTrue(trace.isOperationCompleted())

      assertEquals(1, startedOperations.get())
      assertEquals(1, completedOperations.get())
    }
  }

  @Test
  fun `test super compound parallel execution`() {
    val trace = CompoundParallelOperationTrace<Int>()
    repeat(1000) {
      val latch = CountDownLatch(1)
      val startedOperations = AtomicInteger(0)
      val completedOperations = AtomicInteger(0)
      trace.beforeOperation {
        startedOperations.incrementAndGet()
      }
      trace.afterOperation {
        completedOperations.incrementAndGet()
      }

      val threads = generate(10) {
        thread {
          latch.await()
          trace.startTask(it)
        }
      }
      latch.countDown()
      threads.forEach(Thread::join)
      generate(10, trace::finishTask)
      assertEquals(it.toString(), true, trace.isOperationCompleted())

      assertEquals(1, startedOperations.get())
      assertEquals(1, completedOperations.get())
    }
  }

  @Test
  fun `test operation listener start with TTL`() = testTrace<Int> {
    val isStarted = AtomicBoolean(false)
    val isFinished = AtomicBoolean(false)
    trace.onceBeforeOperation({
      isStarted.set(true)
    }, testDisposable)
    trace.onceAfterOperation({
      isFinished.set(true)
    }, testDisposable)

    operation {
      trace.startTask(1)
      trace.finishTask(1)
    }
    assertTrue(isStarted.get())
    assertTrue(isFinished.get())
  }

  @Test
  fun `test operation listening with TTL`() = testTrace<Int> {
    val ttl = 10
    val unsubscribeIndex = 7

    val startCounter = AtomicInteger(0)
    val finishCounter = AtomicInteger(0)
    val startDisposable = Disposer.newDisposable(testDisposable, "Start operation subscription")
    trace.beforeOperation(ttl, {
      startCounter.incrementAndGet()
    }, startDisposable)
    trace.afterOperation(ttl, {
      finishCounter.incrementAndGet()
    }, testDisposable)

    repeat(ttl) { index ->
      if (index == unsubscribeIndex) {
        Disposer.dispose(startDisposable)
      }
      operation {
        trace.startTask(1)
        trace.finishTask(1)
      }
    }
    operation {
      trace.startTask(1)
      trace.finishTask(1)
    }
    assertEquals(unsubscribeIndex, startCounter.get())
    assertEquals(ttl, finishCounter.get())
  }
}