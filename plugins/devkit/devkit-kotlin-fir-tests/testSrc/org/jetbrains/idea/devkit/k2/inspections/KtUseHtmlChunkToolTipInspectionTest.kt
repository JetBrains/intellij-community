// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.UseHtmlChunkToolTipInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/useHtmlChunkToolTip")
class KtUseHtmlChunkToolTipInspectionTest : UseHtmlChunkToolTipInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useHtmlChunkToolTip"
  }

  override fun getFileExtension(): String = "kt"

  override fun addSetToolTipTextExtensionStub() {
    myFixture.addFileToProject(
      "src/com/intellij/ide/HelpTooltip.kt",
      """
        package com.intellij.ide
        import javax.swing.JComponent
        import com.intellij.openapi.util.text.HtmlChunk
        fun JComponent.setToolTipText(html: HtmlChunk?) {}
      """.trimIndent()
    )
  }

  fun testSetToolTipTextWithString() {
    doTest()
  }

  fun testSetToolTipTextWithStringFix() {
    doTest("Wrap with 'HtmlChunk.text()'")
  }

  fun testSetToolTipTextWithStringFixRaw() {
    doTest("Wrap with 'HtmlChunk.raw()'")
  }

  fun testSetToolTipTextPropertyStyleFix() {
    doTest("Wrap with 'HtmlChunk.text()'")
  }
}
