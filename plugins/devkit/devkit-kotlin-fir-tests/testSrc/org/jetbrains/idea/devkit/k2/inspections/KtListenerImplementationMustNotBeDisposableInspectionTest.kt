// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ListenerImplementationMustNotBeDisposableInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/listenerImplementationMustNotBeDisposable")
class KtListenerImplementationMustNotBeDisposableInspectionTest : ListenerImplementationMustNotBeDisposableInspectionTestBase(),
                                                                  ExpectedPluginModeProvider {

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/listenerImplementationMustNotBeDisposable"

  override fun getFileExtension(): String = "kt"

  fun testDisposableApplicationListener() {
    doTest()
  }

  fun testDisposableProjectListener() {
    doTest()
  }

  fun testDisposableListenerDeepInheritance() {
    doTest()
  }

  fun testDisposableUnregisteredListener() {
    doTest()
  }

  fun testNonDisposableApplicationListener() {
    doTest()
  }

  fun testNonDisposableProjectListener() {
    doTest()
  }

}