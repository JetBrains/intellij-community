// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class EditorConfigInvalidParsingTest : EditorConfigParsingTestBase("invalid") {
  @Test
  fun testKeyWithoutValue() = doTest()
  @Test
  fun testValueWithoutKey() = doTest()
  @Test
  fun testMalformedSection() = doTest()

  @Test
  fun testMalformedVariantWithoutComasWithoutEnd() = doTest()
  @Test
  fun testMalformedVariantWithComasWithoutEnd() = doTest()
  @Test
  fun testMalformedVariantWithoutComasWithoutStart() = doTest()
  @Test
  fun testMalformedVariantWithComasWithoutStart() = doTest()
  @Test
  fun testMalformedVariantWithoutComas() = doTest()
  @Test
  fun testCorruptedPatternEnumeration() = doTest()

  @Test
  fun testMalformedCharclassWithoutStart() = doTest()
  @Test
  fun testMalformedCharclassWithoutEnd() = doTest()

  @Test
  fun testSectionWithSeparator() = doTest()

  @Test
  fun testTooManyTopLevelOptions() = doTest()

  @Test
  fun testMalformedQualifiedKey() = doTest()

  @Test
  fun testTrailingComma() = doTest()
}
