// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.MismatchedLightServiceLevelAndCtorInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
class MismatchedLightServiceLevelAndCtorInspectionTest : MismatchedLightServiceLevelAndCtorInspectionTestBase() {

  private val ANNOTATE_AS_SERVICE_FIX_NAME = "Annotate class 'MyService' as '@Service'"

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/"

  override fun getFileExtension() = "java"

  fun testMakeProjectLevel1() {
    doTest(ANNOTATE_AS_SERVICE_FIX_NAME)
  }

  fun testMakeProjectLevel2() {
    doTest(ANNOTATE_AS_SERVICE_FIX_NAME)
  }

  fun testRemoveProjectParam() {
    doTest("Change method parameters to '()'")
  }

  fun testChangeParamToCoroutineScope() {
    doTest("Change method parameters to '(CoroutineScope scope)'")
  }
}
