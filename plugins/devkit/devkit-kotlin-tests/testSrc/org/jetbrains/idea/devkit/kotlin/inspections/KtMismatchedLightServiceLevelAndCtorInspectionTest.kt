// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.MismatchedLightServiceLevelAndCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
class KtMismatchedLightServiceLevelAndCtorInspectionTest : MismatchedLightServiceLevelAndCtorInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/"

  override fun getFileExtension() = "kt"

  fun testMakeProjectLevel1() {
    doTest("Annotate as @Service")
  }

  fun testMakeProjectLevel2() {
    doTest("Annotate as @Service")
  }

  fun testRemoveProjectParam() {
    doTest("Remove 1st parameter from constructor 'MyService'")
  }

  fun testChangeParamToCoroutineScope() {
    doTest("Change 1st parameter of constructor 'MyService' from 'Project' to 'CoroutineScope'")
  }
}
