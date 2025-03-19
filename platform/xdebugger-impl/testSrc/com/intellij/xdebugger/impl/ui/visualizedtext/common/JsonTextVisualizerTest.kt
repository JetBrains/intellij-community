// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

class JsonTextVisualizerTest : FormattedTextVisualizerTestCase(JsonTextVisualizer()) {

  fun testSomeValidJson() {
    checkPositive(
      """ { "a": 37, "b": "foo" } """,
      """
        {
          "a" : 37,
          "b" : "foo"
        }
      """.trimIndent())
  }

  fun testSomeJsonOnWindows() {
    checkPositive(
      "{\r\n\"a\": 37,\r\n\"b\": \"foo\"\r\n}",
      """
        {
          "a" : 37,
          "b" : "foo"
        }
      """.trimIndent())
  }

  fun testNotJson() {
    checkNegative("Hello, world!")
  }

  fun testPrimitive() {
    checkNegative("123")
  }

  fun testEmptyArray() {
    checkNegative("[]")
  }

  fun testEmptyObject() {
    checkNegative("{}")
  }
}