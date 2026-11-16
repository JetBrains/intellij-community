// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.useHtmlChunkToolTipFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/useHtmlChunkToolTip")
@TestApplication
class KtUseHtmlChunkToolTipInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val htmlChunk by useHtmlChunkToolTipFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useHtmlChunkToolTip")
  private val myFixture get() = htmlChunk.fixture

  @BeforeEach
  fun setUp() {
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

  @Test
  fun testSetToolTipTextWithString() {
    htmlChunk.doTest()
  }

  @Test
  fun testSetToolTipTextWithStringFix() {
    htmlChunk.doTest("Wrap with 'HtmlChunk.text()'")
  }

  @Test
  fun testSetToolTipTextWithStringFixRaw() {
    htmlChunk.doTest("Wrap with 'HtmlChunk.raw()'")
  }

  @Test
  fun testSetToolTipTextPropertyStyleFix() {
    htmlChunk.doTest("Wrap with 'HtmlChunk.text()'")
  }
}
