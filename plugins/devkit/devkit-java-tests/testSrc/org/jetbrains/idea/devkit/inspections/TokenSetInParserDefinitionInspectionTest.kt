// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/tokenSetInParserDefinition")
class TokenSetInParserDefinitionInspectionTest : TokenSetInParserDefinitionInspectionTestBase() {
  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/tokenSetInParserDefinition"
  override fun getFileExtension() = "java"

  fun testParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testNotAParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetWhenComplexCreation() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetWhenStaticImportsUsed() {
    doInspectionTest()
  }

  fun testParserDefinitionNonDirectImplementorWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInitializedInStaticBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSetInitializedInStaticBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenType() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenTypeInitializedInConstructor() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenTypeInitializedInStaticBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalTokenSetInitializedLazilyInMethod() {
    doInspectionTest()
  }

  private fun doInspectionTest() {
    myFixture.copyFileToProject("MyLangTokenTypes.java")
    doTest()
  }

}
