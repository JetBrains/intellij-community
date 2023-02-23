// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
class CancellationCheckInLoopsInspectionTest : CancellationCheckInLoopsInspectionTestBase() {

  override val fileType
    get() = "java"

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops"
  }

  fun testRunCondition() {
    doTest()
  }

  fun testMultipleNestedLoops() {
    doTest()
  }

  fun testPresentCancellationCheck() {
    doTest()
  }

}
