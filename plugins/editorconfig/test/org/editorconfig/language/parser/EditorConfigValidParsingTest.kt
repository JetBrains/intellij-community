// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

class EditorConfigValidParsingTest : EditorConfigParsingTestBase("valid") {
  fun testEmpty() = doTest()
  fun testKeyValuePair() = doTest()
  fun testSimpleSection() = doTest()
  fun testCharclassSection() = doTest()
  fun testVariantSection() = doTest()
  fun testComplexSection() = doTest()
  fun testTwoSections() = doTest()
  fun testComplexFile() = doTest()
  fun testMultipleValues() = doTest()
  fun testMultipleValuesWithMultipleOptions() = doTest()
  fun testTrailingComment() = doTest()
  fun testComplexPair() = doTest()
  fun testComplexHeader() = doTest()
  fun testQualifiedKey() = doTest()
  fun testCharclassInEnumeration() = doTest()
  fun testOptionWithSimpleRawValue() = doTest()
  fun testOptionWithComplexRawValue() = doTest()
  fun testOptionWithEmptyRawValue() = doTest()
}
