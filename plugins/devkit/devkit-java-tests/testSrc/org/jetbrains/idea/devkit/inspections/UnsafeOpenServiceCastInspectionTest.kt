// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/unsafeOpenServiceCast")
@TestApplication
internal class UnsafeOpenServiceCastInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val openServiceCast by unsafeOpenServiceCastFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unsafeOpenServiceCast/")

  @Test
  fun testUnsafeCastOfOpenService() {
    openServiceCast.doTest("UnsafeCastOfOpenService.java", "openService.xml")
  }

  @Test
  fun testSafeCastOfNonOpenService() {
    openServiceCast.doTest("SafeCastOfNonOpenService.java", "nonOpenService.xml")
  }

  @Test
  fun testServiceRetrievalPatterns() {
    openServiceCast.doTest("ServiceRetrievalPatterns.java", "openService.xml")
  }

  @Test
  fun testNonOpenServiceRetrievalPatterns() {
    openServiceCast.doTest("NonOpenServiceRetrievalPatterns.java", "nonOpenService.xml")
  }
}
