// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace
import junit.framework.TestCase

abstract class CompoundParallelOperationTraceTestCase : TestCase() {

  protected fun <R> generate(times: Int, action: (Int) -> R): Iterable<R> {
    return (0 until times).map(action)
  }

  protected fun <Id> testTrace(action: TestTraceContext<Id>.() -> Unit) {
    val trace = CompoundParallelOperationTrace<Id>()
    assertTrue(trace.isOperationCompleted())
    TestTraceContext(trace).action()
    assertTrue(trace.isOperationCompleted())
  }

  data class TestTraceContext<Id>(private val delegate: CompoundParallelOperationTrace<Id>) {

    private var mustBeComplete = true

    val trace = MockCompoundParallelOperationTrace(delegate)

    fun operation(action: () -> Unit) {
      assertTrue(delegate.isOperationCompleted())
      assertTrue(mustBeComplete)
      action()
      assertTrue(delegate.isOperationCompleted())
      mustBeComplete = true
    }

    inner class MockCompoundParallelOperationTrace<Id>(private val delegate: CompoundParallelOperationTrace<Id>) {
      fun startTask(taskId: Id) {
        assertEquals(mustBeComplete, delegate.isOperationCompleted())
        delegate.startTask(taskId)
        mustBeComplete = false
      }

      fun finishTask(taskId: Id) {
        assertEquals(mustBeComplete, delegate.isOperationCompleted())
        delegate.finishTask(taskId)
      }
    }
  }
}