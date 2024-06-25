// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

class UrlEncodedTextVisualizerTest : FormattedTextVisualizerTestCase(UrlEncodedTextVisualizer()) {

  fun testSomeValidUrl() {
    checkPositive(
      "http://google.com/search?q=Hello%2C+world%21",
      "http://google.com/search?q=Hello, world!")
  }

  fun testPartial() {
    checkPositive(
      "Hello,%20world!",
      "Hello, world!")
  }

  fun testNothingEscaped() {
    checkNegative("Hello, world!")
  }

  fun testArithmeticWhichIsNotLikelyToBeURL() {
    checkNegative("123+456")
  }
}