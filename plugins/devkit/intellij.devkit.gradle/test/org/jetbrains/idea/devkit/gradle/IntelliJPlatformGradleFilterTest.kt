// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.filters.Filter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class IntelliJPlatformGradleFilterTest : LightJavaCodeInsightFixtureTestCase() {

  fun testFilterHighlightsPrefixAtStartOfLine() {
    val text = "[org.jetbrains.intellij.platform] Starting build"
    val result = applyFilter(text)

    val item = assertOneElement(result.resultItems)
    assertHighlightingRange(item, text, 0, 33)

    assertEquals(ConsoleViewContentType.LOG_INFO_OUTPUT.attributes, item.highlightAttributes)
    assertEquals(Filter.NextAction.CONTINUE_FILTERING, result.nextAction)
  }

  fun testFilterHighlightsPrefixInMiddleOfLine() {
    val text = "12:34:56.789 [org.jetbrains.intellij.platform] Target IntelliJ Platform: 2026.1"
    val result = applyFilter(text)

    val item = assertOneElement(result.resultItems)
    assertHighlightingRange(item, text, 13, 46)
    assertEquals(Filter.NextAction.CONTINUE_FILTERING, result.nextAction)
  }

  fun testFilterHighlightsMultiplePrefixes() {
    val text = "[org.jetbrains.intellij.platform] foo [org.jetbrains.intellij.platform] bar"
    val result = applyFilter(text)

    assertEquals(2, result.resultItems.size)
    assertHighlightingRange(result.resultItems[0], text, 0, 33)
    assertHighlightingRange(result.resultItems[1], text, 38, 71)
  }

  fun testFilterIgnoresOtherLines() {
    val filter = IntelliJPlatformGradleFilter()
    val result = filter.applyFilter("BUILD SUCCESSFUL in 2s", 22)
    assertNull(result)
  }

  private fun applyFilter(text: String): Filter.Result {
    val filter = IntelliJPlatformGradleFilter()
    val result = filter.applyFilter(text, text.length)
    assertNotNull(result)
    return result!!
  }

  private fun assertHighlightingRange(resultItem: Filter.ResultItem, text: String, expectedStartOffset: Int, expectedEndOffset: Int) {
    val highlightedText = text.substring(resultItem.highlightStartOffset, resultItem.highlightEndOffset)
    val expectedHighlightedText = text.substring(expectedStartOffset, expectedEndOffset)
    val assertionMessage = "Highlighted '$highlightedText', but expected '$expectedHighlightedText'"
    assertEquals(assertionMessage, expectedStartOffset, resultItem.highlightStartOffset)
    assertEquals(assertionMessage, expectedEndOffset, resultItem.highlightEndOffset)
  }
}
