// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCommonMainOnly
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val STRINGS_FILE_PATH = "composeApp/src/commonMain/composeResources/values/strings.xml"
private const val OTHER_XML_FILE_PATH = "composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml"

@ComposeResourcesCommonMainOnly
class ComposeResourcesXmlAnnotatorTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test highlighting in strings xml`() = doHighlightingTest(
    relativePath = STRINGS_FILE_PATH,
    content = $$"""
      <resources>
        <string name="test_special">Special characters: \n, \t, \u0020</string>
        <string name="test_placeholder">Placeholder: %1$s, %2$d</string>
        <string-array name="test_array">
          <item>Item with \n</item>
        </string-array>
      </resources>
    """.trimIndent(),
  ) { highlights ->
    assertHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    assertHighlight(highlights, "\\t", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    assertHighlight(highlights, "\\u0020", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    assertHighlight(highlights, $$"%1$s", DefaultLanguageHighlighterColors.CONSTANT)
    assertHighlight(highlights, $$"%2$d", DefaultLanguageHighlighterColors.CONSTANT)
    assertHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, 2)
  }

  @Test
  fun `test no highlighting in other xml files`() = doHighlightingTest(
    relativePath = OTHER_XML_FILE_PATH,
    content = $$"""
      <resources>
        <string name="test_special">Not highlighted: \n, %1$s</string>
      </resources>
    """.trimIndent(),
  ) { highlights ->
    assertNoHighlight(highlights, "\\n", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    assertNoHighlight(highlights, $$"%1$s", DefaultLanguageHighlighterColors.CONSTANT)
  }

  /**
   * Writes the content to the project file, then highlights it.
   * `writeTextAndCommit` snapshots the file, so the Gradle fixture restores it after the test.
   */
  private fun doHighlightingTest(
    relativePath: String,
    content: String,
    assertions: (List<HighlightInfo>) -> Unit,
  ) = testComposeResourcesProject {
    writeTextAndCommit(relativePath, content)

    codeInsightFixture.configureFromExistingVirtualFile(canonicalProjectFile(relativePath))
    assertions(codeInsightFixture.doHighlighting())
  }

  private fun assertHighlight(
    highlights: List<HighlightInfo>,
    text: String,
    attributeKey: TextAttributesKey,
    occurrence: Int = 1,
  ) {
    val matches = highlights.filter {
      it.forcedTextAttributesKey == attributeKey &&
      codeInsightFixture.file.text.substring(it.startOffset, it.endOffset) == text
    }
    assertTrue(matches.size >= occurrence, "Highlight not found for '$text' with attribute '$attributeKey'")
  }

  private fun assertNoHighlight(
    highlights: List<HighlightInfo>,
    text: String,
    attributeKey: TextAttributesKey,
  ) {
    val matches = highlights.filter {
      it.forcedTextAttributesKey == attributeKey &&
      codeInsightFixture.file.text.substring(it.startOffset, it.endOffset) == text
    }
    assertTrue(matches.isEmpty(), "Highlight found for '$text' with attribute '$attributeKey' but it shouldn't be highlighted")
  }
}
