// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionClassShouldNotBeOpenAndPublic")
internal class ExtensionClassShouldNotBeOpenAndPublicInspectionTest : ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/extensionClassShouldNotBeOpenAndPublic/"
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
    myFixture.configureByFile("plugin.xml")
  }

  fun testMakeNotOpen() {
    doTest("Make 'MyInspection' not open")
  }

  fun testMakeInternal() {
    doTest("Make 'MyInspection' internal")
  }

  fun testInternalFinalExtensionClass() {
    doTest()
  }

  fun testHasInheritor() {
    doTest()
  }

  fun testVisibleForTestingAnnotation() {
    doTest()
  }
}