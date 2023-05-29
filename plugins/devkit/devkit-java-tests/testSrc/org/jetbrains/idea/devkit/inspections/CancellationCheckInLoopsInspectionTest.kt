// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
class CancellationCheckInLoopsInspectionTest : CancellationCheckInLoopsInspectionTestBase() {

  override fun getFileExtension(): String = "java"

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops"

  fun testRunCondition() {
    doTest()
  }

  fun testRunConditionOnSuperMethod() {
    doTest()
  }

  fun testPresentCancellationCheck() {
    doTest()
  }

  fun testCancellationCheckPresentNotInFirstLine() {
    doTest()
  }

  fun testForEachLoops() {
    doTest()
  }

  fun testForLoops() {
    doTest()
  }

  fun testWhileLoops() {
    doTest()
  }

  fun testDoWhileLoops() {
    doTest()
  }

  fun testNestedLoops() {
    doTest()
  }

}
