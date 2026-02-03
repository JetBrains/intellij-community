// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/wrapInSupplierFix")
class WrapInSupplierFixTest : ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/wrapInSupplierFix"

  override fun getFileExtension(): String = "java"

  private val quickFixName = "Wrap application service in 'java.util.function.Supplier'"

  fun testWrapFieldInSupplier() {
    doFixTest(quickFixName)
  }
}
