// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.codeInsight

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.module.Module
import com.intellij.testFramework.TestDataPath
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.inspections.PluginXmlRegistrationCheckInspectionTestBase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

/**
 * The Kotlin counterpart of `org.jetbrains.idea.devkit.codeInsight.PluginXmlRegistrationCheckInspectionTest`.
 * The registered classes come from Kotlin sources, so the inspection must resolve them through the Kotlin light classes.
 */
@TestDataPath("\$CONTENT_ROOT/testData/inspections/pluginXmlRegistrationCheck")
class KtPluginXmlRegistrationCheckInspectionTest : PluginXmlRegistrationCheckInspectionTestBase() {

  override val sourceFileExtension: String = "kt"

  override fun getBasePath(): @NonNls String {
    return PluginPathManager.getPluginHomePathRelative("devkit") +
           "/devkit-kotlin-fir-tests/testData/inspections/pluginXmlRegistrationCheck"
  }

  override fun configureLanguageRuntime(module: Module) {
    ConfigLibraryUtil.configureKotlinRuntime(module)
  }

  fun testRegistrationCheck() {
    doTestRegistrationCheck()
  }
}
