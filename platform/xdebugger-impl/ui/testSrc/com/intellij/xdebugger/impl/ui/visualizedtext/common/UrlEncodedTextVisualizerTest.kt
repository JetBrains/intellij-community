// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class UrlEncodedTextVisualizerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val checker = TextVisualizerChecker(UrlEncodedTextVisualizer(), projectFixture)

  @Test
  fun testSomeValidUrl(): Unit = timeoutRunBlocking {
    checker.checkPositive(
      "http://google.com/search?q=Hello%2C+world%21",
      "http://google.com/search?q=Hello, world!")
  }

  @Test
  fun testPartial(): Unit = timeoutRunBlocking {
    checker.checkPositive(
      "Hello,%20world!",
      "Hello, world!")
  }

  @Test
  fun testNothingEscaped() {
    checker.checkNegative("Hello, world!")
  }

  @Test
  fun testArithmeticWhichIsNotLikelyToBeURL() {
    checker.checkNegative("123+456")
  }
}