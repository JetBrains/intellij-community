// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/tokenSetInParserDefinition")
@TestApplication
class TokenSetInParserDefinitionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val tokenSet by tokenSetInParserDefinitionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/tokenSetInParserDefinition")
  private val myFixture get() = tokenSet.fixture

  @Test
  fun testParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testNotAParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetWhenComplexCreation() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetWhenStaticImportsUsed() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionNonDirectImplementorWithIllegalTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInitializedInStaticBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSetInitializedInStaticBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenType() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenTypeInitializedInConstructor() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenTypeInitializedInStaticBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalTokenSetInitializedLazilyInMethod() {
    doInspectionTest()
  }

  private fun doInspectionTest() {
    myFixture.copyFileToProject("MyLangTokenTypes.java")
    tokenSet.doTest()
  }

}
