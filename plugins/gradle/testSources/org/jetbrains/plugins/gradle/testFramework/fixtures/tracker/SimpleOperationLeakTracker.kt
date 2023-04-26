// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture


class SimpleOperationLeakTracker(
  private val getOperation: (Disposable) -> ObservableOperationTrace
) : IdeaTestFixture {

  private lateinit var testDisposable: Disposable

  private lateinit var operationTracker: OperationLeakTracker

  private lateinit var operation: ObservableOperationTrace

  override fun setUp() {
    testDisposable = Disposer.newDisposable()

    operation = getOperation(testDisposable)

    operationTracker = OperationLeakTracker()
    operationTracker.setUp()
    operationTracker.installOperationWatcher(operation, testDisposable)
  }

  fun <R> withAllowedOperation(numTasks: Int, action: () -> R): R {
    return operationTracker.withAllowedOperation(operation, numTasks, action)
  }

  suspend fun <R> withAllowedOperationAsync(numTasks: Int, action: suspend () -> R): R {
    return operationTracker.withAllowedOperationAsync(operation, numTasks, action)
  }

  override fun tearDown() {
    runAll(
      { operationTracker.tearDown() },
      { Disposer.dispose(testDisposable) }
    )
  }
}