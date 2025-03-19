// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class JUnit5DisposableFixture {
  companion object {
    private const val DISPOSABLE_ID = "[engine:junit-jupiter]/[class:com.intellij.testFramework.junit5.showcase.JUnit5DisposableFixture]/[test-template:test()]"

    private val classLevelDisposable = disposableFixture()

    private var previousTestLevelDisposable: CheckedDisposable? = null
    private var previousClassLevelDisposable: CheckedDisposable? = null
  }

  private val testLevelDisposable = disposableFixture()


  @ParameterizedTest
  @ValueSource(ints = [1, 2])
  fun test() {
    val disposableDebugString = testLevelDisposable.get().toString()
    assertTrue(DISPOSABLE_ID in disposableDebugString,
               "$DISPOSABLE_ID must be in $disposableDebugString")
    previousTestLevelDisposable?.let {
      assertTrue(it.isDisposed, "Test-level disposable from previous run must be disposed")
    }
    previousClassLevelDisposable?.let {
      assertFalse(it.isDisposed, "Class-level disposable from previous run must NOT be disposed")
    }
    val classLevelDisposable = classLevelDisposable.get()
    Disposer.register(classLevelDisposable, Disposer.newDisposable())
    val testLevelDisposable = testLevelDisposable.get()
    Disposer.register(testLevelDisposable, Disposer.newDisposable())
    previousTestLevelDisposable = testLevelDisposable as CheckedDisposable
    previousClassLevelDisposable = classLevelDisposable as CheckedDisposable
  }
}