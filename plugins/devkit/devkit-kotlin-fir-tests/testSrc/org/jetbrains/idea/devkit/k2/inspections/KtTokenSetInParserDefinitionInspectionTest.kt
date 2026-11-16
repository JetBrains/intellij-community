// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.tokenSetInParserDefinitionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/tokenSetInParserDefinition")
@TestApplication
class KtTokenSetInParserDefinitionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val tokenSet by tokenSetInParserDefinitionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/tokenSetInParserDefinition")
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
  fun testParserDefinitionNonDirectImplementorWithIllegalTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInCompanionObject() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInitializedInCompanionObjectInitBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetInitializedInInitBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithIllegalTokenSetWhenMembersImported() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSet() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSetInCompanionObject() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSetInitializedInCompanionObjectInitBlock() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalCoreTokenSetInitializedInConstructor() {
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
  fun testParserDefinitionWithLegalTokenSetDeclaredOnTopLevel() {
    doInspectionTest()
  }

  @Test
  fun testParserDefinitionWithLegalTokenSetInitializedLazilyInMethod() {
    doInspectionTest()
  }

  private fun doInspectionTest() {
    myFixture.copyFileToProject("MyLangTokenTypes.kt")
    tokenSet.doTest()
  }
}
