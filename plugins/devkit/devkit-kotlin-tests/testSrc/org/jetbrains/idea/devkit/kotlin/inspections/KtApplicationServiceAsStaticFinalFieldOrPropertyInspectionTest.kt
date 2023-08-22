// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("/inspections/applicationServiceAsStaticFinalFieldOrProperty")
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

  // TODO: enable after fixing ServiceUtil.kt (classes, annotated with @Service without any arguments
  //  are treated as APP service if defined in Java, and NOT_SPECIFIED if they are defined in Kotlin,
  //  so here the inspection doesn't detect application services)

  // Test SSR inspection "Eager service initialization during classloading"
  fun _testEagerInitializationDuringClassloading() {
    doHighlightTest()
  }

}
