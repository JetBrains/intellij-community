// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.observable

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace
import com.intellij.openapi.observable.operations.ObservableOperationTrace
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase

abstract class CompoundParallelOperationTraceTestCase : TestCase() {

  protected lateinit var testDisposable: Disposable

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()
  }

  override fun tearDown() {
    try {
      Disposer.dispose(testDisposable)
    }
    finally {
      super.tearDown()
    }
  }

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

    inner class MockCompoundParallelOperationTrace<Id>(
      private val delegate: CompoundParallelOperationTrace<Id>
    ): ObservableOperationTrace by delegate {
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