// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.RetrievingServiceInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/retrievingService")
internal class RetrievingServiceInspectionTest : RetrievingServiceInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/retrievingService/"

  override fun getFileExtension() = "java"

  fun testAppLevelServiceAsProjectLevel() {
    doTest()
  }

  fun testProjectLevelServiceAsAppLevel() {
    doTest()
  }

  fun testReplaceWithGetInstanceApplicationLevel() {
    doTest(DevKitBundle.message("inspection.retrieving.service.replace.with", "MyService", "getInstance"))
  }

  fun testReplaceWithGetInstanceProjectLevel() {
    doTest(DevKitBundle.message("inspection.retrieving.service.replace.with", "MyService", "getInstance"))
  }
}
