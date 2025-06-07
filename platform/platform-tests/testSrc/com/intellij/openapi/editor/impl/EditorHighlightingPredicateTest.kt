// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.ThreadingAssertions

class EditorHighlightingPredicateTest : AbstractEditorTest() {
  fun testHighlightingPredicates() {
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

  fun testReplacingPredicate() {
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
    val editor = this.editor as EditorImpl
    editor.addHighlightingPredicate(key, EditorHighlightingPredicate { highlighter ->
      highlighter != highlighterToExclude
    })
  }

  private fun removeHighlightingPredicate(key: Key<EditorHighlightingPredicate>) {
    val editor = this.editor as EditorImpl
    editor.removeHighlightingPredicate(key)
  }

  private fun assertEditorMarkupContains(
    contains: List<RangeHighlighter>,
    doesNotContain: List<RangeHighlighter>,
  ) {
    val allHighlighters = (editor as EditorImpl).filteredDocumentMarkupModel.allHighlighters.toSet()

    for (highlighter in contains) {
      assertTrue("$allHighlighters does not contain $highlighter", highlighter in allHighlighters)
    }

    for (highlighter in doesNotContain) {
      assertTrue("$allHighlighters contains $highlighter", highlighter !in allHighlighters)
    }
  }

  private fun addHighlighter(): RangeHighlighter {
    val markupModel = DocumentMarkupModel.forDocument(getDocument(file), project, true)
    return markupModel.addRangeHighlighter(0, 3, HighlighterLayer.SYNTAX, null, HighlighterTargetArea.EXACT_RANGE)
  }
}

private val key1 = Key.create<EditorHighlightingPredicate>("key1")
private val key2 = Key.create<EditorHighlightingPredicate>("key2")
