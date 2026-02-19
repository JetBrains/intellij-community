// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

class EditorConfigInvalidParsingTest : EditorConfigParsingTestBase("invalid") {
  fun testKeyWithoutValue() = doTest()
  fun testValueWithoutKey() = doTest()
  fun testMalformedSection() = doTest()

  fun testMalformedVariantWithoutComasWithoutEnd() = doTest()
  fun testMalformedVariantWithComasWithoutEnd() = doTest()
  fun testMalformedVariantWithoutComasWithoutStart() = doTest()
  fun testMalformedVariantWithComasWithoutStart() = doTest()
  fun testMalformedVariantWithoutComas() = doTest()
  fun testCorruptedPatternEnumeration() = doTest()

  fun testMalformedCharclassWithoutStart() = doTest()
  fun testMalformedCharclassWithoutEnd() = doTest()

  fun testSectionWithSeparator() = doTest()

  fun testTooManyTopLevelOptions() = doTest()

  fun testMalformedQualifiedKey() = doTest()

  fun testTrailingComma() = doTest()
}
