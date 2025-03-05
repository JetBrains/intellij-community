// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/companionObjectInExtension")
abstract class KtCompanionObjectInExtensionInspectionTest : KtCompanionObjectInExtensionInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/companionObjectInExtension"

  fun testNoHighlighting() {
    doTest()
  }

  fun testEmptyBlockCompanionObject() {
    doTest()
  }

  fun testEmptyCompanionObject() {
    doTest()
  }

  fun testExtensionWithCompanionObject() {
    doTest()
  }

  fun testExtensionWithLoggerAndConstVal() {
    doTest()
  }

  fun testExtensionWithInitBlocks() {
    doTest()
  }
}
