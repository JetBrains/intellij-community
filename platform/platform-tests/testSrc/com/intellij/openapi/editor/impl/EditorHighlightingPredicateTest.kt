// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.ThreadingAssertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class EditorHighlightingPredicateTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testHighlightingPredicates(): Unit = fixture.runTest {
    ThreadingAssertions.assertEventDispatchThread()

    initText("foo bar baz xyz")

    val highlighter1 = addHighlighter()
    val highlighter2 = addHighlighter()
    val highlighter3 = addHighlighter()

    addHighlightingPredicate(key1, highlighter1)
    assertEditorMarkupContains(contains = listOf(highlighter2, highlighter3), doesNotContain = listOf(highlighter1))

    addHighlightingPredicate(key2, highlighter2)
    assertEditorMarkupContains(contains = listOf(highlighter3), doesNotContain = listOf(highlighter1, highlighter2))

    removeHighlightingPredicate(key1)
    assertEditorMarkupContains(contains = listOf(highlighter1, highlighter3), doesNotContain = listOf(highlighter2))

    removeHighlightingPredicate(key2)
    assertEditorMarkupContains(contains = listOf(highlighter1, highlighter2, highlighter3), doesNotContain = emptyList())
  }

  @Test
  fun testReplacingPredicate(): Unit = fixture.runTest {
    ThreadingAssertions.assertEventDispatchThread()

    initText("foo bar baz xyz")

    val highlighter1 = addHighlighter()
    val highlighter2 = addHighlighter()
    val highlighter3 = addHighlighter()

    addHighlightingPredicate(key1, highlighter1)
    addHighlightingPredicate(key2, highlighter2)
    assertEditorMarkupContains(contains = listOf(highlighter3), doesNotContain = listOf(highlighter1, highlighter2))

    addHighlightingPredicate(key1, highlighter3)
    assertEditorMarkupContains(contains = listOf(highlighter1), doesNotContain = listOf(highlighter2, highlighter3))
  }

  private fun addHighlightingPredicate(key: Key<EditorHighlightingPredicate>, highlighterToExclude: RangeHighlighter) {
    val editor = fixture.editor as EditorImpl
    editor.addHighlightingPredicate(key, EditorHighlightingPredicate { highlighter ->
      highlighter != highlighterToExclude
    })
  }

  private fun removeHighlightingPredicate(key: Key<EditorHighlightingPredicate>) {
    val editor = fixture.editor as EditorImpl
    editor.removeHighlightingPredicate(key)
  }

  private fun assertEditorMarkupContains(
    contains: List<RangeHighlighter>,
    doesNotContain: List<RangeHighlighter>,
  ) {
    val allHighlighters = (fixture.editor as EditorImpl).filteredDocumentMarkupModel.allHighlighters.toSet()

    for (highlighter in contains) {
      assertTrue(highlighter in allHighlighters, "$allHighlighters does not contain $highlighter")
    }

    for (highlighter in doesNotContain) {
      assertTrue(highlighter !in allHighlighters, "$allHighlighters contains $highlighter")
    }
  }

  private fun addHighlighter(): RangeHighlighter {
    val markupModel = DocumentMarkupModel.forDocument(fixture.editor.document, projectFixture.get(), true)
    return markupModel.addRangeHighlighter(0, 3, HighlighterLayer.SYNTAX, null, HighlighterTargetArea.EXACT_RANGE)
  }
}

private val key1 = Key.create<EditorHighlightingPredicate>("key1")
private val key2 = Key.create<EditorHighlightingPredicate>("key2")
