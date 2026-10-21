// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class EditorConfigValidParsingTest : EditorConfigParsingTestBase("valid") {
  @Test
  fun testEmpty() = doTest()
  @Test
  fun testKeyValuePair() = doTest()
  @Test
  fun testSimpleSection() = doTest()
  @Test
  fun testCharclassSection() = doTest()
  @Test
  fun testVariantSection() = doTest()
  @Test
  fun testComplexSection() = doTest()
  @Test
  fun testTwoSections() = doTest()
  @Test
  fun testComplexFile() = doTest()
  @Test
  fun testMultipleValues() = doTest()
  @Test
  fun testMultipleValuesWithMultipleOptions() = doTest()
  @Test
  fun testTrailingComment() = doTest()
  @Test
  fun testComplexPair() = doTest()
  @Test
  fun testComplexHeader() = doTest()
  @Test
  fun testQualifiedKey() = doTest()
  @Test
  fun testCharclassInEnumeration() = doTest()
  @Test
  fun testOptionWithSimpleRawValue() = doTest()
  @Test
  fun testOptionWithComplexRawValue() = doTest()
  @Test
  fun testOptionWithEmptyRawValue() = doTest()
}
