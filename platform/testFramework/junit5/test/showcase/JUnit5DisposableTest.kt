// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JUnit5DisposableTest {

  @TestDisposable
  lateinit var disposableField: Disposable

  @Test
  fun disposable(@TestDisposable disposableParam: Disposable, @TestDisposable anotherDisposableParam: Disposable) {
    Assertions.assertSame(disposableField, disposableParam)
    Assertions.assertSame(disposableParam, anotherDisposableParam)
    Assertions.assertFalse((disposableParam as CheckedDisposable).isDisposed)
    Assertions.assertEquals(
      "[engine:junit-jupiter]/" +
      "[class:com.intellij.testFramework.junit5.showcase.JUnit5DisposableTest]/" +
      "[method:disposable(com.intellij.openapi.Disposable, com.intellij.openapi.Disposable)]" +
      "{isDisposed=false}",
      disposableParam.toString()
    )
  }
}
