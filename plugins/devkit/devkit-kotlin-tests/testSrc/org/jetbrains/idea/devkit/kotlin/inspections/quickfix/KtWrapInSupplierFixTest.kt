// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.inspections.applicationServiceAsStaticFinalFieldOrPropertyFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/wrapInSupplierFix")
abstract class KtWrapInSupplierFixTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val appService by applicationServiceAsStaticFinalFieldOrPropertyFixture(projectFixture,
    "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/wrapInSupplierFix")

  private val fixName = "Wrap application service in 'java.util.function.Supplier'"

  @Test
  fun testWrapTopLevelPropertyInSupplier() {
    appService.doFixTest(fixName)
  }

  @Test
  fun testWrapCompanionObjectPropertyInSupplier() {
    appService.doFixTest(fixName)
  }

  @Test
  fun testWrapObjectPropertyInSupplier() {
    appService.doFixTest(fixName)
  }
}
