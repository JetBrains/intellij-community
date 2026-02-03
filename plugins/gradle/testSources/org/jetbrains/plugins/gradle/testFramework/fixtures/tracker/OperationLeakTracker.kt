// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationCompleted
import com.intellij.openapi.observable.operation.core.whenOperationScheduled
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.dumpThreads
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicInteger

class OperationLeakTracker(
  private val getOperation: (Disposable) -> ObservableOperationTrace
) : IdeaTestFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var operation: ObservableOperationTrace

  private lateinit var eventLeakTracker: EventLeakTracker

  private lateinit var expectedCounter: AtomicInteger
  private lateinit var actualCounter: AtomicInteger

  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()

    operation = getOperation(fixtureDisposable)

    expectedCounter = AtomicInteger(0)
    actualCounter = AtomicInteger(0)

    eventLeakTracker = EventLeakTracker(operation.name)
    eventLeakTracker.setUp()

    installOperationWatcher()
  }

  override fun tearDown() {
    runAll(
      { eventLeakTracker.tearDown() },
      { assertOperationState() },
      { Disposer.dispose(fixtureDisposable) }
    )
  }

  private fun installOperationWatcher() {
    operation.whenOperationScheduled(fixtureDisposable) {
      eventLeakTracker.assertEventIsAllowed("SCHEDULE")
    }
    operation.whenOperationStarted(fixtureDisposable) {
      eventLeakTracker.assertEventIsAllowed("START")
      actualCounter.incrementAndGet()
    }
  }

  fun <R> withAllowedOperation(numTasks: Int, action: () -> R): R {
    return withAllowedOperationImpl(numTasks) {
      eventLeakTracker.withAllowedOperationEvents(action)
    }
  }

  suspend fun <R> withAllowedOperationAsync(numTasks: Int, action: suspend () -> R): R {
    return withAllowedOperationImpl(numTasks) {
      eventLeakTracker.withAllowedOperationEventsAsync(action)
    }
  }

  private inline fun <R> withAllowedOperationImpl(
    numTasks: Int,
    action: () -> R
  ): R {
    assertOperationState()
    expectedCounter.addAndGet(numTasks)
    val result = action()
    assertOperationState()
    return result
  }

  private fun assertOperationState() {
    Assertions.assertTrue(operation.isOperationCompleted()) {
      "Operation should be completed before assertion $operation\n" +
      dumpThreads(operation.name) + "\n"
    }
    Assertions.assertEquals(expectedCounter.get(), actualCounter.get()) {
      "Operation counter assertion $operation"
    }
  }
}