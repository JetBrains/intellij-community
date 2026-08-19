// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.LightProjectDescriptorEqualsHashCodeInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightProjectDescriptorEqualsHashCode")
class KtLightProjectDescriptorEqualsHashCodeInspectionTest : LightProjectDescriptorEqualsHashCodeInspectionTestBase() {

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightProjectDescriptorEqualsHashCode"

  override fun getFileExtension(): String = "kt"

  fun testFlagged() {
    doTest()
  }

  fun testNotFlagged() {
    doTest()
  }
}
