// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.VirtualFileUrlConversionInspectionTestBase

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/virtualFileUrlConversion")
class VirtualFileUrlConversionInspectionTest : VirtualFileUrlConversionInspectionTestBase() {
  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/virtualFileUrlConversion"
  }

  override fun getFileExtension(): String = "java"

  fun testConversions() {
    doTest()
  }
}
