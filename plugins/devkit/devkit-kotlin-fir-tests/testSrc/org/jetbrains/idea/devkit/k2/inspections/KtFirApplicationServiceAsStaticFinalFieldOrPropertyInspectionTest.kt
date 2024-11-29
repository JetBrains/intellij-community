// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.inspections.ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("/inspections/applicationServiceAsStaticFinalFieldOrProperty")
class KtFirApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest : ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase(), ExpectedPluginModeProvider {

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun getBasePath() = PluginPathManager.getPluginHomePathRelative("devkit") + "/devkit-kotlin-tests/testData/inspections/applicationServiceAsStaticFinalFieldOrProperty"

  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    setUpWithKotlinPlugin {  super.setUp() }
  }

  override fun tearDown() {
    runAll(
      { runInEdtAndWait { project.invalidateCaches() } },
      { super.tearDown() },
    )
  }

  fun testStaticProperties() {
    doHighlightTest()
  }

  fun testExplicitConstructorCall() {
    doHighlightTest()
  }

  fun testNonServices() {
    doHighlightTest()
  }

  fun testRegisteredServices() {
    doHighlightTest()
  }

  fun testLightServices() {
    doHighlightTest()
  }

  fun testBackingFields() {
    doHighlightTest()
  }

  fun testServiceInstances() {
    doHighlightTest()
  }

  fun testEagerInitializationDuringClassloading() {
    doHighlightTest()
  }
}
