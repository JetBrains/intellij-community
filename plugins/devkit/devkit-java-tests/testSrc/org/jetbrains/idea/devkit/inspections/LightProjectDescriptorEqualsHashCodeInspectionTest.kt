// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.LightProjectDescriptorEqualsHashCodeInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightProjectDescriptorEqualsHashCode")
internal class LightProjectDescriptorEqualsHashCodeInspectionTest : LightProjectDescriptorEqualsHashCodeInspectionTestBase() {

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightProjectDescriptorEqualsHashCode/"

  override fun getFileExtension(): String = "java"

  fun testFlagged() {
    doTest()
  }

  fun testNotFlagged() {
    doTest()
  }
}
