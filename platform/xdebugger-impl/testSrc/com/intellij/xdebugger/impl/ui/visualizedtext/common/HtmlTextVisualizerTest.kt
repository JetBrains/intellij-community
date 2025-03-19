// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

class HtmlTextVisualizerTest : TextVisualizerTestCase(HtmlTextVisualizer()) {

  fun testSomeValidHtml() {
    checkPositive(" <p>Hello, world!</p> ")
  }

  fun testSomeValidHtmlWithCRLF() {
    checkPositive("<p>\r\nHello, world!\r\n</p>")
  }

  fun testNotSoValidButOkHtml() {
    checkPositive("<p>Hello, <b>world</b>!")
  }

  fun testNotHtml() {
    checkNegative("Hello, world!")
  }

  fun testNotStandaloneHtml() {
    checkNegative("Hello, <b>world</b>!")
  }
}