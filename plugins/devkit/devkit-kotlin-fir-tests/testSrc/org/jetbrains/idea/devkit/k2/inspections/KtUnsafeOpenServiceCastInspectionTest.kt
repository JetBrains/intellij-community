// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.UnsafeOpenServiceCastInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/unsafeOpenServiceCast")
internal class KtUnsafeOpenServiceCastInspectionTest : UnsafeOpenServiceCastInspectionTestBase() {

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/unsafeOpenServiceCast/"

  override fun getFileExtension(): String = "kt"

  fun testUnsafeCastOfOpenService() {
    doTest("UnsafeCastOfOpenService.kt", "openService.xml")
  }

  fun testServiceRetrievalPatterns() {
    doTest("ServiceRetrievalPatterns.kt", "openService.xml")
  }

  fun testNonOpenServiceRetrievalPatterns() {
    doTest("NonOpenServiceRetrievalPatterns.kt", "nonOpenService.xml")
  }
}