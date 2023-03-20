// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.util.getGradleReloadOperation

class ESReloadLeakTracker : IdeaTestFixture {

  private lateinit var testDisposable: Disposable

  private lateinit var operationTracker: ESOperationLeakTracker

  private lateinit var reloadOperation: ObservableOperationTrace

  override fun setUp() {
    testDisposable = Disposer.newDisposable()

    reloadOperation = getGradleReloadOperation(testDisposable)

    operationTracker = ESOperationLeakTracker()
    operationTracker.setUp()
    operationTracker.installOperationWatcher(reloadOperation, testDisposable)
  }

  suspend fun <R> withAllowedReload(numReloads: Int = 1, action: suspend () -> R): R {
    return operationTracker.withAllowedOperation(reloadOperation, numReloads, action)
  }

  fun assertReloadState() {
    operationTracker.assertOperationAllOperationsState()
  }

  override fun tearDown() {
    runAll(
      { operationTracker.tearDown() },
      { Disposer.dispose(testDisposable) }
    )
  }
}