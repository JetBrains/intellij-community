// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class JsonTextVisualizerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val checker = TextVisualizerChecker(JsonTextVisualizer(), projectFixture)

  @Test
  fun testSomeValidJson(): Unit = timeoutRunBlocking {
    checker.checkPositive(
      """ { "a": 37, "b": "foo" } """,
      """
        {
          "a" : 37,
          "b" : "foo"
        }
      """.trimIndent())
  }

  @Test
  fun testSomeJsonOnWindows(): Unit = timeoutRunBlocking {
    checker.checkPositive(
      "{\r\n\"a\": 37,\r\n\"b\": \"foo\"\r\n}",
      """
        {
          "a" : 37,
          "b" : "foo"
        }
      """.trimIndent())
  }

  @Test
  fun testNotJson() {
    checker.checkNegative("Hello, world!")
  }

  @Test
  fun testPrimitive() {
    checker.checkNegative("123")
  }

  @Test
  fun testEmptyArray() {
    checker.checkNegative("[]")
  }

  @Test
  fun testEmptyObject() {
    checker.checkNegative("{}")
  }
}