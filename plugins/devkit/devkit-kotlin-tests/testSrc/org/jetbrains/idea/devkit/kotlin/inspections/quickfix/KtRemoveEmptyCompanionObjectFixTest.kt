// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("/inspections/removeEmptyCompanionObjectFix")
class KtRemoveEmptyCompanionObjectFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/removeEmptyCompanionObjectFix"

  private val quickFixName = DevKitKotlinBundle.message("inspections.remove.empty.companion.object.fix.text")

  fun testEmptyBlockCompanionObject() {
    doTest(quickFixName)
  }

  fun testEmptyCompanionObject() {
    doTest(quickFixName)
  }

}