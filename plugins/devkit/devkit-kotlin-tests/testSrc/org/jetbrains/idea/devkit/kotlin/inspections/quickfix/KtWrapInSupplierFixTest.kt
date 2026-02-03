// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider

@TestDataPath("\$CONTENT_ROOT/testData/inspections/wrapInSupplierFix")
abstract class KtWrapInSupplierFixTest : ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase(), ExpectedPluginModeProvider {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/wrapInSupplierFix"

  override fun getFileExtension(): String = "kt"

  private val fixName = "Wrap application service in 'java.util.function.Supplier'"

  fun testWrapTopLevelPropertyInSupplier() {
    doFixTest(fixName)
  }

  fun testWrapCompanionObjectPropertyInSupplier() {
    doFixTest(fixName)
  }

  fun testWrapObjectPropertyInSupplier() {
    doFixTest(fixName)
  }
}
