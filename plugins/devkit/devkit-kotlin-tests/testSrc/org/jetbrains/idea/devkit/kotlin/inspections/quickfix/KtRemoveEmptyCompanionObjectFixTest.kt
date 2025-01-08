// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/removeEmptyCompanionObjectFix")
class KtRemoveEmptyCompanionObjectFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/removeEmptyCompanionObjectFix"

  private val quickFixName = "Remove empty companion object"

  fun testEmptyBlockCompanionObject() {
    doTest(quickFixName)
  }

  fun testEmptyCompanionObject() {
    doTest(quickFixName)
  }

}