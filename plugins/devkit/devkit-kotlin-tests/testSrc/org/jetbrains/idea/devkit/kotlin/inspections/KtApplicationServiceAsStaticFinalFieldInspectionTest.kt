// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalField")
class KtApplicationServiceAsStaticFinalFieldInspectionTest : ApplicationServiceAsStaticFinalFieldInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalField"

  override val fileType: String
    get() = "kt"

  fun testNonServices() {
    doTest()
  }

  fun testRegisteredServices() {
    doTest()
  }

  fun testExplicitConstructorCall() {
    doTest()
  }

  fun testLightServices() {
    doTest()
  }

}