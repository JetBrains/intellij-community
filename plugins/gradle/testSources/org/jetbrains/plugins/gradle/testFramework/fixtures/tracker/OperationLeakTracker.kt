// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationCompleted
import com.intellij.openapi.observable.operation.core.whenOperationScheduled
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.dumpThreads
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OperationLeakTracker : IdeaTestFixture {

  private lateinit var allowedOperations: ConcurrentHashMap<ObservableOperationTrace, OperationState>

  override fun setUp() {
    allowedOperations = ConcurrentHashMap()
  }

  override fun tearDown() {
    assertOperationAllOperationStates()
  }

  @BeforeEach
  fun setupOperations() {
    allowedOperations = ConcurrentHashMap()
  }

  fun installOperationWatcher(operation: ObservableOperationTrace, parentDisposable: Disposable) {
    operation.whenOperationScheduled(parentDisposable) {
      val state = getState(operation)
      Assertions.assertTrue(state.isAllowed.get()) {
        "Unexpected operation $operation\n" +
        dumpThreads(operation.name) + "\n"
      }
    }
    operation.whenOperationStarted(parentDisposable) {
      val state = getState(operation)
      state.actualCounter.incrementAndGet()
      Assertions.assertTrue(state.isAllowed.get()) {
        "Unexpected operation $operation\n" +
        dumpThreads(operation.name) + "\n"
      }
    }
  }

  fun <R> withAllowedOperation(
    operation: ObservableOperationTrace,
    numTasks: Int,
    action: () -> R
  ): R {
    return withAllowedOperationImpl(operation, numTasks) { action() }
  }

  suspend fun <R> withAllowedOperationAsync(
    operation: ObservableOperationTrace,
    numTasks: Int,
    action: suspend () -> R
  ): R {
    return withAllowedOperationImpl(operation, numTasks) { action() }
  }

  private inline fun <R> withAllowedOperationImpl(
    operation: ObservableOperationTrace,
    numTasks: Int,
    action: () -> R
  ): R {
    val state = allowedOperations.computeIfAbsent(operation) { OperationState() }

    assertOperationState(operation, state)
    state.expectedCounter.addAndGet(numTasks)
    state.isAllowed.set(true)
    val result = try {
      action()
    }
    finally {
      state.isAllowed.set(false)
    }
    assertOperationState(operation, state)
    return result
  }

  private fun assertOperationAllOperationStates() {
    runAll(allowedOperations.entries) { (operation, state) ->
      assertOperationState(operation, state)
    }
  }

  private fun getState(operation: ObservableOperationTrace): OperationState {
    return allowedOperations.computeIfAbsent(operation) { OperationState() }
  }

  private fun assertOperationState(operation: ObservableOperationTrace, state: OperationState) {
    Assertions.assertFalse(state.isAllowed.get()) {
      "Operation should be completed before assertion $operation\n" +
      dumpThreads(operation.name) + "\n"
    }
    Assertions.assertTrue(operation.isOperationCompleted()) {
      "Operation should be completed before assertion $operation\n" +
      dumpThreads(operation.name) + "\n"
    }
    Assertions.assertEquals(state.expectedCounter.get(), state.actualCounter.get()) {
      "Operation counter assertion $operation"
    }
  }

  private class OperationState {
    val isAllowed = AtomicBoolean(false)
    val actualCounter = AtomicInteger(0)
    val expectedCounter = AtomicInteger(0)
  }
}