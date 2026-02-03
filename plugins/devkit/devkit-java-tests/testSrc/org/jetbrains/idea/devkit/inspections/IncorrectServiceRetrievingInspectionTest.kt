// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectServiceRetrieving")
internal class IncorrectServiceRetrievingInspectionTest : IncorrectServiceRetrievingInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectServiceRetrieving/"

  override fun getFileExtension() = "java"

  fun testAppLevelServiceAsProjectLevel() {
    doTest(true)
  }

  fun testProjectLevelServiceAsAppLevel() {
    doTest(true)
  }

  fun testUnregisteredService() {
    doTest(true)
  }
}
