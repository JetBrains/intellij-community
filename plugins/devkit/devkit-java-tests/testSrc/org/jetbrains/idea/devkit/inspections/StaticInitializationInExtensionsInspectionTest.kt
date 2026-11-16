// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/staticInitializationInExtensions")
@TestApplication
class StaticInitializationInExtensionsInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val staticInit by staticInitializationInExtensionsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/staticInitializationInExtensions")

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