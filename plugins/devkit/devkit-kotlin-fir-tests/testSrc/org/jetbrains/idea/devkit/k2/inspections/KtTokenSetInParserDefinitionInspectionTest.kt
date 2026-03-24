// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.TokenSetInParserDefinitionInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/tokenSetInParserDefinition")
class KtTokenSetInParserDefinitionInspectionTest : TokenSetInParserDefinitionInspectionTestBase(), ExpectedPluginModeProvider {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/tokenSetInParserDefinition"
  override fun getFileExtension() = "kt"

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  fun testParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testNotAParserDefinitionWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetWhenComplexCreation() {
    doInspectionTest()
  }

  fun testParserDefinitionNonDirectImplementorWithIllegalTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInCompanionObject() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInitializedInCompanionObjectInitBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInitializedInConstructor() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetInitializedInInitBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithIllegalTokenSetWhenMembersImported() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSet() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSetInCompanionObject() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSetInitializedInCompanionObjectInitBlock() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalCoreTokenSetInitializedInConstructor() {
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

  fun testParserDefinitionWithLegalTokenSetDeclaredOnTopLevel() {
    doInspectionTest()
  }

  fun testParserDefinitionWithLegalTokenSetInitializedLazilyInMethod() {
    doInspectionTest()
  }


  private fun doInspectionTest() {
    myFixture.copyFileToProject("MyLangTokenTypes.kt")
    doTest()
  }
}
