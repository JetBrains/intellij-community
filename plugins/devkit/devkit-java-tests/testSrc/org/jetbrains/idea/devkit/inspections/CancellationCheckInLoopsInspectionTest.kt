// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
@TestApplication
class CancellationCheckInLoopsInspectionTest {
  companion object {
    private val projectFixture = cancellationCheckInLoopsProjectFixture()
  }

  private val cancellationCheck by cancellationCheckInLoopsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops")

  @Test
  fun testRunCondition() {
    cancellationCheck.doTest()
  }

  @Test
  fun testRunConditionOnSuperMethod() {
    cancellationCheck.doTest()
  }

  @Test
  fun testPresentCancellationCheck() {
    cancellationCheck.doTest()
  }

  @Test
  fun testCancellationCheckPresentNotInFirstLine() {
    cancellationCheck.doTest()
  }

  @Test
  fun testForEachLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testForLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testWhileLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testDoWhileLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testNestedLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testLoopMethods() {
    cancellationCheck.doTest()
  }

  @Test
  fun testNestedLoopMethods() {
    cancellationCheck.doTest()
  }

}
