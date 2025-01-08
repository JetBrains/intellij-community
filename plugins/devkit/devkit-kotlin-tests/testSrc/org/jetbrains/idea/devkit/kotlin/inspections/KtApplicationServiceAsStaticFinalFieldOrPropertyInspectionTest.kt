// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalFieldOrProperty")
class KtApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest : ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalFieldOrProperty"

  override fun getFileExtension(): String = "kt"


  fun testStaticProperties() {
    doHighlightTest()
  }

  fun testExplicitConstructorCall() {
    doHighlightTest()
  }

  fun testNonServices() {
    doHighlightTest()
  }

  fun testRegisteredServices() {
    doHighlightTest()
  }

  fun testLightServices() {
    doHighlightTest()
  }

  fun testBackingFields() {
    doHighlightTest()
  }

  fun testServiceInstances() {
    doHighlightTest()
  }

  fun testEagerInitializationDuringClassloading() {
    doHighlightTest()
  }

}
