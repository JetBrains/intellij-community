// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class InstanceIElementTypeFieldInspectionKotlinTest : InstanceIElementTypeFieldInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  override fun getFileExtension(): String = "kt"

  fun testInstancePropertyWithIElementType() {
    doTest()
  }

  fun testCompanionObjectNoWarning() {
    doTest()
  }

  fun testObjectDeclarationNoWarning() {
    doTest()
  }

  fun testQualifiedNameWithLanguage() {
    doTest()
  }

  fun testEnumFieldsNoWarning() {
    doTest()
  }
}
