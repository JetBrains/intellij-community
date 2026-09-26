// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/staticInitializationInExtensions")
class StaticInitializationInExtensionsInspectionTest : StaticInitializationInExtensionsInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/staticInitializationInExtensions"

  override fun getFileExtension(): String = "java"

  fun testExtensionWithStaticInitialization() {
    doTest()
  }

  fun testExtensionWithoutStaticInitialization() {
    doTest()
  }

  fun testNonExtension() {
    doTest()
  }

}