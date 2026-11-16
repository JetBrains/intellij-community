// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.inspections.applicationServiceAsStaticFinalFieldOrPropertyFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalFieldOrProperty")
abstract class KtApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val appService by applicationServiceAsStaticFinalFieldOrPropertyFixture(projectFixture,
    "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalFieldOrProperty")

  @Test
  fun testStaticProperties() {
    appService.doHighlightTest()
  }

  @Test
  fun testExplicitConstructorCall() {
    appService.doHighlightTest()
  }

  @Test
  fun testNonServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testRegisteredServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testLightServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testBackingFields() {
    appService.doHighlightTest()
  }

  @Test
  fun testServiceInstances() {
    appService.doHighlightTest()
  }

  @Test
  fun testEagerInitializationDuringClassloading() {
    appService.doHighlightTest()
  }

}
