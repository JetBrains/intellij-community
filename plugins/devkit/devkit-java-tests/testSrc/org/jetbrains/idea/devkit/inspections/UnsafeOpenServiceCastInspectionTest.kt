// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/unsafeOpenServiceCast")
internal class UnsafeOpenServiceCastInspectionTest : UnsafeOpenServiceCastInspectionTestBase() {

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unsafeOpenServiceCast/"

  override fun getFileExtension(): String = "java"

  fun testUnsafeCastOfOpenService() {
    doTest("UnsafeCastOfOpenService.java", "openService.xml")
  }

  fun testSafeCastOfNonOpenService() {
    doTest("SafeCastOfNonOpenService.java", "nonOpenService.xml")
  }

  fun testServiceRetrievalPatterns() {
    doTest("ServiceRetrievalPatterns.java", "openService.xml")
  }

  fun testNonOpenServiceRetrievalPatterns() {
    doTest("NonOpenServiceRetrievalPatterns.java", "nonOpenService.xml")
  }
}
