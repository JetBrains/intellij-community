// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.virtualFileUrlConversionFixture
import org.junit.jupiter.api.Test

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/virtualFileUrlConversion")
@TestApplication
class VirtualFileUrlConversionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val urlConversion by virtualFileUrlConversionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/virtualFileUrlConversion")

  @Test
  fun testConversions() {
    urlConversion.doTest()
  }
}
