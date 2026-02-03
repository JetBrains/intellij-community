// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.observable

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.ObservableOperationStatus
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals

abstract class AtomicOperationTraceTestCase {

  protected lateinit var testDisposable: Disposable

  @BeforeEach
  fun setUp() {
    testDisposable = Disposer.newDisposable()
  }

  @AfterEach
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  fun <R> generate(times: Int, action: (Int) -> R): Iterable<R> {
    return (0 until times).map(action)
  }

  fun assertOperationState(trace: ObservableOperationTrace, state: ObservableOperationStatus) {
    assertEquals(state, trace.status, trace.toString())
  }
}