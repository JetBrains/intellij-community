// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.StaticInitializationInExtensionsInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("/inspections/staticInitializationInExtensions")
class KtStaticInitializationInExtensionsInspectionTest : StaticInitializationInExtensionsInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/staticInitializationInExtensions"

  override fun getFileExtension(): String = "kt"

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