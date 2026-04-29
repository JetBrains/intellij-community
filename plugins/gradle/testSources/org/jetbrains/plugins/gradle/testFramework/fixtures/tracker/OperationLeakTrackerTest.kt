// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class OperationLeakTrackerTest {

  @Test
  fun `setUp and tearDown without operations`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    tracker.tearDown()
  }

  @Test
  fun `single operation inside allowed window`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    tracker.withAllowedOperation(numTasks = 1) {
      trace.traceSchedule()
      trace.traceStart()
      trace.traceFinish()
    }
    tracker.tearDown()
  }

  @Test
  fun `two operations inside allowed window`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    tracker.withAllowedOperation(numTasks = 2) {
      repeat(2) {
        trace.traceSchedule()
        trace.traceStart()
        trace.traceFinish()
      }
    }
    tracker.tearDown()
  }

  @Test
  fun `multiple sequential allowed windows`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    repeat(3) {
      tracker.withAllowedOperation(numTasks = 1) {
        trace.traceSchedule()
        trace.traceStart()
        trace.traceFinish()
      }
    }
    tracker.tearDown()
  }

  @Test
  fun `schedule outside allowed window throws`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    assertThrows(AssertionError::class.java) {
      trace.traceSchedule()
    }
    trace.detachAll()
    tracker.tearDown()
  }

  @Test
  fun `start outside allowed window throws`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    assertThrows(AssertionError::class.java) {
      trace.traceStart()
    }
    trace.traceFinish()
    tracker.tearDown()
  }

  @Test
  fun `fewer starts than expected throws`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    assertThrows(AssertionError::class.java) {
      tracker.withAllowedOperation(numTasks = 2) {
        trace.traceSchedule()
        trace.traceStart()
        trace.traceFinish()
      }
    }
    tracker.tearDown()
  }

  @Test
  fun `more starts than expected throws`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    assertThrows(AssertionError::class.java) {
      tracker.withAllowedOperation(numTasks = 1) {
        repeat(2) {
          trace.traceSchedule()
          trace.traceStart()
          trace.traceFinish()
        }
      }
    }
    tracker.tearDown()
  }

  @Test
  fun `tearDown waits for leaked operation that completes before tearDown`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()

    // Start operation inside window but don't finish it — withAllowedOperation detects that
    // and throws; trace remains IN_PROGRESS
    runCatching {
      tracker.withAllowedOperation(numTasks = 1) {
        trace.traceSchedule()
        trace.traceStart()
      }
    }

    // complete the leaked operation synchronously before tearDown
    trace.traceFinish()
    // operation is already COMPLETED — returns immediately without error
    tracker.tearDown()
  }

  @RepeatedTest(10)
  fun `tearDown waits for leaked operation that completes concurrently`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()

    runCatching {
      tracker.withAllowedOperation(numTasks = 1) {
        trace.traceSchedule()
        trace.traceStart()
      }
    }

    // Finish the in-progress operation from a background thread while tearDown is waiting
    val finisherThread = thread {
      Thread.sleep(10)
      trace.traceFinish()
    }

    // blocks until traceFinish fires the finish event
    tracker.tearDown()
    finisherThread.join()
  }

  @Test
  fun `tearDown succeeds after operation counter mismatch`() {
    val trace = AtomicOperationTrace("test")
    val tracker = OperationLeakTracker { trace }
    tracker.setUp()
    runCatching {
      tracker.withAllowedOperation(numTasks = 2) {
        trace.traceSchedule()
        trace.traceStart()
        trace.traceFinish()
      }
    }
    tracker.tearDown()
  }
}
