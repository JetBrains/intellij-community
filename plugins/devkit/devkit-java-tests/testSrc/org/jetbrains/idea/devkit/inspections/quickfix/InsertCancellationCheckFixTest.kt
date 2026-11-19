// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsFixture
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsProjectFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/insertCancellationCheckFix")
@TestApplication
class InsertCancellationCheckFixTest {
  companion object {
    private val projectFixture = cancellationCheckInLoopsProjectFixture()
  }

  private val cancellationCheck by cancellationCheckInLoopsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/insertCancellationCheckFix")

  private val fixName = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  // loops:

  @Test
  fun testBlockDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockForLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyForLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineForLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNoBodyForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNoBodyForLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNoBodyWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  // loop methods:

  @Test
  fun testBlockIterableForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockIteratorForEachRemainingMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockStreamForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockStreamForEachOrderedMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockMapForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyIterableForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleStreamForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockContainerUtilProcessMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNestedLoopMethods() {
    cancellationCheck.doTest(fixName)
  }

  // Kotlin methods:

  @Test
  fun testBlockArraysKtForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

}
