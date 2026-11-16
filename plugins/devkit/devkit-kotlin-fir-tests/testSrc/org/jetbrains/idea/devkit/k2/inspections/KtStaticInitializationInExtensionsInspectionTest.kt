// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.jetbrains.idea.devkit.inspections.staticInitializationInExtensionsFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/staticInitializationInExtensions")
@TestApplication
class KtStaticInitializationInExtensionsInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val staticInit by staticInitializationInExtensionsFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/staticInitializationInExtensions")

  @Test
  fun testExtensionWithStaticInitialization() {
    staticInit.doTest()
  }

  @Test
  fun testExtensionWithoutStaticInitialization() {
    staticInit.doTest()
  }

  @Test
  fun testNonExtension() {
    staticInit.doTest()
  }

}