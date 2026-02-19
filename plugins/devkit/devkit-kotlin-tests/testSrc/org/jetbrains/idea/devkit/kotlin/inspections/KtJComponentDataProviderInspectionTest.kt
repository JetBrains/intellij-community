// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.JComponentDataProviderInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/jComponentDataProvider")
class KtJComponentDataProviderInspectionTest : JComponentDataProviderInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  override fun getFileExtension() = "kt"

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "/inspections/jComponentDataProvider/"

  fun testObject() {
    doTest()
  }

  fun testNotJComponent() {
    doTest()
  }

  fun testMyJComponent() {
    doTest()
  }

}