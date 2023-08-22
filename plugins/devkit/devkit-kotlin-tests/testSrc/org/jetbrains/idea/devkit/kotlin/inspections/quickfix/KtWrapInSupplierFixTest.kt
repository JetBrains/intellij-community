// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/wrapInSupplierFix")
class KtWrapInSupplierFixTest : ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/wrapInSupplierFix"

  override fun getFileExtension(): String = "kt"


  private val fixName = DevKitBundle.message("inspections.wrap.application.service.in.supplier.quick.fix.message")


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
