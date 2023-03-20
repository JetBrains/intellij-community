// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.RetrievingServiceInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/retrievingService")
class KtRetrievingServiceInspectionTest : RetrievingServiceInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/retrievingService/"

  override fun getFileExtension() = "kt"

  fun testAppLevelServiceAsProjectLevel() {
    doTest()
  }

  fun testProjectLevelServiceAsAppLevel() {
    doTest()
  }
}
