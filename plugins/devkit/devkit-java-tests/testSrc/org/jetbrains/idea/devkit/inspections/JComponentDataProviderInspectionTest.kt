// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/jComponentDataProvider")
class JComponentDataProviderInspectionTest : LightDevKitInspectionFixTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/jComponentDataProvider/"

  override fun getFileExtension(): String = "java"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JComponentDataProviderInspection())

    myFixture.addClass("""
      package com.intellij.openapi.actionSystem;
      public interface DataProvider {}
    """.trimIndent())

    myFixture.addClass("""
      package com.intellij.openapi.actionSystem;
      public interface UiCompatibleDataProvider {}
    """.trimIndent())

    myFixture.addClass("""
      package com.intellij.openapi.actionSystem;
      public interface UiDataProvider {}
    """.trimIndent())
  }

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