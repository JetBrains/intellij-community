// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class HtmlTextVisualizerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val checker = TextVisualizerChecker(HtmlTextVisualizer(), projectFixture)

  @Test
  fun testSomeValidHtml(): Unit = timeoutRunBlocking {
    checker.checkPositive(" <p>Hello, world!</p> ")
  }

  @Test
  fun testSomeValidHtmlWithCRLF(): Unit = timeoutRunBlocking {
    checker.checkPositive("<p>\r\nHello, world!\r\n</p>")
  }

  @Test
  fun testNotSoValidButOkHtml(): Unit = timeoutRunBlocking {
    checker.checkPositive("<p>Hello, <b>world</b>!")
  }

  @Test
  fun testNotHtml() {
    checker.checkNegative("Hello, world!")
  }

  @Test
  fun testNotStandaloneHtml() {
    checker.checkNegative("Hello, <b>world</b>!")
  }

  @Test
  fun testEmpty() {
    checker.checkNegative("")
  }
}