// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.codeInsight.enableComposeInTest
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

internal class ComposeMissingPluginInspectionWithoutConfigurationExtensionTest : KotlinLightCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // No extra module configuration is performed here, so the module is perceived as simple JPS module for which there is
    // no Compose configuration extension.
    myFixture.enableComposeInTest()
    myFixture.enableInspections(ComposeMissingPluginInspection() as InspectionProfileEntry)
  }

  fun `test inspection is skipped when there is no applicable configuration extension`() {
    myFixture.configureByText(
      "Test.kt",
      """
        import androidx.compose.runtime.Composable

        @Composable fun MyComposable() {}

        fun caller() {
          MyComposable()
        }
      """.trimIndent(),
    )

    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .filter { it.description == ComposeIdeBundle.message("compose.inspection.missing.plugin.name") }

    assertTrue(errors.isEmpty())
  }
}
