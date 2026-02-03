// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/jComponentDataProvider")
class JComponentDataProviderInspectionTest : JComponentDataProviderInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/jComponentDataProvider/"

  override fun getFileExtension(): String = "java"

  fun testNotJComponent() {
    doTest()
  }

  fun testMyJComponent() {
    doTest()
  }

  fun testMyJComponentUiCompatibleDataProvider() {
    doTest()
  }
}