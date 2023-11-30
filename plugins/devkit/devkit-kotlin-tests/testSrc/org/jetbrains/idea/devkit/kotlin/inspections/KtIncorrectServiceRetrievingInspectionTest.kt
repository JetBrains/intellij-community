// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.IncorrectServiceRetrievingInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectServiceRetrieving")
internal class KtIncorrectServiceRetrievingInspectionTest : IncorrectServiceRetrievingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile("service.kt")
    myFixture.configureByFile("services.kt")
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectServiceRetrieving/"

  override fun getFileExtension() = "kt"

  fun testRetrievingServiceAsProjectLevel() {
    doTest(true)
  }

  fun testRetrievingServiceAsAppLevel() {
    doTest(true)
  }

  fun testUnregisteredServices() {
    doTest(true)
  }

  fun testUnspecifiedLevelService() {
    doTest(false)
  }
}
