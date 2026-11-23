// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalFieldOrProperty")
@TestApplication
class ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val appService by applicationServiceAsStaticFinalFieldOrPropertyFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalFieldOrProperty")

  @Test
  fun testNonServicesInFields() {
    appService.doHighlightTest()
  }

  @Test
  fun testRegisteredServicesInFields() {
    appService.doHighlightTest()
 }

  @Test
  fun testExplicitConstructorCallInFields() {
    appService.doHighlightTest()
  }

  @Test
  fun testLightServicesInFields() {
    appService.doHighlightTest()
  }
}
