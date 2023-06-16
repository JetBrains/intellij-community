// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.CancellationCheckInLoopsInspectionTestBase


@TestDataPath("\$CONTENT_ROOT/testData/inspections/insertCancellationCheckFix")
class InsertCancellationCheckFixTest : CancellationCheckInLoopsInspectionTestBase() {

  override fun getFileExtension(): String = "java"

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/insertCancellationCheckFix"

  private val fixName = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  fun testBlockDoWhileLoop() {
    doTest(fixName)
  }

  fun testBlockForEachLoop() {
    doTest(fixName)
  }

  fun testBlockForLoop() {
    doTest(fixName)
  }

  fun testBlockWhileLoop() {
    doTest(fixName)
  }
  
  fun testEmptyDoWhileLoop() {
    doTest(fixName)
  }

  fun testEmptyForEachLoop() {
    doTest(fixName)
  }

  fun testEmptyForLoop() {
    doTest(fixName)
  }

  fun testEmptyWhileLoop() {
    doTest(fixName)
  }

  fun testSingleLineDoWhileLoop() {
    doTest(fixName)
  }

  fun testSingleLineForEachLoop() {
    doTest(fixName)
  }

  fun testSingleLineForLoop() {
    doTest(fixName)
  }

  fun testSingleLineWhileLoop() {
    doTest(fixName)
  }

  fun testNoBodyForEachLoop() {
    doTest(fixName)
  }

  fun testNoBodyForLoop() {
    doTest(fixName)
  }

  fun testNoBodyWhileLoop() {
    doTest(fixName)
  }

}
