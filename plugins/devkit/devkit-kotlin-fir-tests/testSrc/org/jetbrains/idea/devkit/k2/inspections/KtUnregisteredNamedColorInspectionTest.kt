// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import org.jetbrains.idea.devkit.themes.UnregisteredNamedColorInspectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KtUnregisteredNamedColorInspectionTest : UnregisteredNamedColorInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  fun testInspection() {
    myFixture.configureByText("InspectionTest.kt", """
      import com.intellij.ui.JBColor

      class InspectionTest {
        fun smth() {
          JBColor.namedColor("${knownNamedColor}", 0xcdcdcd)
          JBColor.<warning descr="Named color key 'NotRegisteredKey' is not registered in '*.themeMetadata.json' (Documentation)">namedColor</warning>("NotRegisteredKey", 0xcdcdcd)
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}