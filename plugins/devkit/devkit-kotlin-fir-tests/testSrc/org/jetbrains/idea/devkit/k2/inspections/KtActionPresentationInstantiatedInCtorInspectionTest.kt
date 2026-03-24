// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ActionPresentationInstantiatedInCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/actionPresentationInstantiatedInCtor")
internal class KtActionPresentationInstantiatedInCtorInspectionTest : ActionPresentationInstantiatedInCtorInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/actionPresentationInstantiatedInCtor/"
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
    setPluginXml("plugin.xml")
  }

  fun testDefaultCtorNegative() { doTest() }

  fun testDefaultCtorPositive() { doTest() }

  fun testPrimaryCtorNegative() { doTest() }

  fun testPrimaryCtorPositive() { doTest() }

  fun testSecondaryCtors() { doTest() }

  fun testUnregisteredAction() { doTest() }

  fun testCtorCallNegative() { doTest() }

  fun testCtorCallPositive() { doTest() }
}
