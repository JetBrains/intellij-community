// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import kotlin.test.assertNotNull as kAssertNotNull

class ComposeResourcesXmlAnnotatorTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test highlighting in strings xml`() {
    val files = importProjectFromTestData()
    val stringsFile = files.findStringsFile("commonMain")

    val content = $$"""
        <resources>
          <string name="test_special">Special characters: \n, \t, \u0020</string>
          <string name="test_placeholder">Placeholder: %1$s, %2$d</string>
          <string-array name="test_array">
            <item>Item with \n</item>
          </string-array>
        </resources>
      """.trimIndent()

    timeoutRunBlocking(context = Dispatchers.EDT) {

      runWriteAction {

        codeInsightTestFixture.saveText(stringsFile, content)
      }

      codeInsightTestFixture.configureFromExistingVirtualFile(stringsFile)
      val highlights = codeInsightTestFixture.doHighlighting()

      assertHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
      assertHighlight(highlights, "\\t", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
      assertHighlight(highlights, "\\u0020", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
      assertHighlight(highlights, $$"%1$s", DefaultLanguageHighlighterColors.CONSTANT)
      assertHighlight(highlights, $$"%2$d", DefaultLanguageHighlighterColors.CONSTANT)
      assertHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, 2)
    }
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test no highlighting in other xml files`() {
    val files = importProjectFromTestData()
    val otherFile = files.findOtherXmlFile("commonMain")

    val content = $$"""
        <resources>
          <string name="test_special">Not highlighted: \n, %1$s</string>
        </resources>
      """.trimIndent()

    timeoutRunBlocking(context = Dispatchers.EDT) {

    runWriteAction {
        codeInsightTestFixture.saveText(otherFile, content)
      }

      codeInsightTestFixture.configureFromExistingVirtualFile(otherFile)
      val highlights = codeInsightTestFixture.doHighlighting()

      assertNoHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
      assertNoHighlight(highlights, $$"%1$s", DefaultLanguageHighlighterColors.CONSTANT)
    }
  }

  private fun List<VirtualFile>.findStringsFile(sourceSetName: String): VirtualFile {
    val stringsFile = firstOrNull { it.path.endsWith("composeApp/src/$sourceSetName/composeResources/values/strings.xml") }
    kAssertNotNull(stringsFile, "Strings file not found for source set '$sourceSetName'")
    return stringsFile
  }

  private fun List<VirtualFile>.findOtherXmlFile(sourceSetName: String): VirtualFile {
    val otherXmlFile =
      firstOrNull { it.path.contains("composeApp/src/$sourceSetName/composeResources/drawable") && it.name.endsWith(".xml") }
    kAssertNotNull(otherXmlFile, "Other XML file not found for source set '$sourceSetName'")
    return otherXmlFile
  }

  private fun assertHighlight(
    highlights: List<HighlightInfo>,
    text: String,
    attributeKey: TextAttributesKey,
    occurrence: Int = 1,
  ) {
    val matches = highlights.filter {
      it.forcedTextAttributesKey == attributeKey &&
      codeInsightTestFixture.file.text.substring(it.startOffset, it.endOffset) == text
    }
    assertTrue("Highlight not found for '$text' with attribute '$attributeKey'", matches.size >= occurrence)
  }

  private fun assertNoHighlight(
    highlights: List<HighlightInfo>,
    text: String,
    attributeKey: TextAttributesKey,
  ) {
    val matches = highlights.filter {
      it.forcedTextAttributesKey == attributeKey &&
      codeInsightTestFixture.file.text.substring(it.startOffset, it.endOffset) == text
    }
    assertTrue("Highlight found for '$text' with attribute '$attributeKey' but it shouldn't be highlighted", matches.isEmpty())
  }
}
