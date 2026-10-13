// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
internal class ADParsingTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val parsing by parsingFixture(
    projectFixture,
    "$adTestDataPath/parser",
    "ad",
    true,
    ADParserDefinition(),
  )

  @Test
  fun testSimple() = doTest()

  @Test
  fun testField() = doTest()

  @Test
  fun testRecover() = doTest()

  @Test
  fun testRecover1() = doTest()

  @Test
  fun testRecover2() = doTest()

  private fun doTest() {
    parsing.doTest(true)
  }
}
