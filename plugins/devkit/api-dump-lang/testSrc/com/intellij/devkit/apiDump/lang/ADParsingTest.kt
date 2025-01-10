// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.testFramework.ParsingTestCase

internal class ADParsingTest : ParsingTestCase(
  /* dataPath = */ "parser",
  /* fileExt = */ "ad",
  /* lowercaseFirstLetter = */ true,
  /* ...definitions = */ ADParserDefinition()
) {
  fun testSimple() = doTest()
  fun testField() = doTest()

  private fun doTest() {
    doTest(true)
  }

  override fun skipSpaces(): Boolean =
    false

  override fun getTestDataPath(): String? =
    adTestDataPath
}