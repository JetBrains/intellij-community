// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.incorrectServiceRetrievingFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectServiceRetrieving")
@TestApplication
internal class KtIncorrectServiceRetrievingInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val serviceRetrieving by incorrectServiceRetrievingFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectServiceRetrieving/")
  private val myFixture get() = serviceRetrieving.fixture

  @BeforeEach
  fun setUp() {
    myFixture.configureByFile("service.kt")
    myFixture.configureByFile("services.kt")
  }

  @Test
  fun testRetrievingServiceAsProjectLevel() {
    serviceRetrieving.doTest(true)
  }

  @Test
  fun testRetrievingServiceAsAppLevel() {
    serviceRetrieving.doTest(true)
  }

  @Test
  fun testUnregisteredServices() {
    serviceRetrieving.doTest(true)
  }

  @Test
  fun testUnspecifiedLevelService() {
    serviceRetrieving.doTest(false)
  }
}
