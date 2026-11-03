// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests the rule of [ProblemsViewContextVisibility.isVisible]. */
class ProblemsViewContextVisibilityTest {
  private val a = TestCodeInsightContext("a")
  private val b = TestCodeInsightContext("b")
  private val dirty: (CodeInsightContext) -> Boolean = { false }
  private val analyzed: (CodeInsightContext) -> Boolean = { true }

  @Test
  fun `a problem without a context or with any context is always visible`() {
    val visibility = visibility(editorContexts = emptyList())

    assertTrue(visibility.isVisible(null, dirty), "Expected the global group to be visible")
    assertTrue(visibility.isVisible(anyContext(), dirty), "Expected any context to be visible")
  }

  @Test
  fun `the context of an editor is visible when dirty`() {
    assertTrue(visibility(editorContexts = listOf(a)).isVisible(a, dirty))
  }

  @Test
  fun `an analyzed context without an editor is visible`() {
    assertTrue(visibility(editorContexts = emptyList()).isVisible(a, analyzed))
  }

  @Test
  fun `a dirty context without an editor is hidden`() {
    assertFalse(visibility(editorContexts = emptyList()).isVisible(a, dirty))
  }

  @Test
  fun `one context only shows the context of the selected editor`() {
    val visibility = visibility(editorContexts = listOf(a, b), oneContextOnly = true, onlyContext = a)

    assertTrue(visibility.isVisible(a, analyzed), "Expected the context of the selected editor to be visible")
    assertFalse(visibility.isVisible(b, analyzed), "Expected the context of the other editor to be hidden")
  }

  @Test
  fun `one context only shows the preferred context when no editor context is known`() {
    val visibility = visibility(editorContexts = emptyList(), oneContextOnly = true, onlyContext = b)

    assertTrue(visibility.isVisible(b, analyzed), "Expected the preferred context to be visible")
    assertFalse(visibility.isVisible(a, analyzed), "Expected the other context to be hidden")
  }

  @Test
  fun `one context only hides the preferred context when it is dirty`() {
    val visibility = visibility(editorContexts = emptyList(), oneContextOnly = true, onlyContext = b)

    assertFalse(visibility.isVisible(b) { it != b }, "Expected the dirty preferred context to be hidden")
    assertFalse(visibility.isVisible(a) { it != b }, "Expected the other context to be hidden")
  }

  @Test
  fun `one context only hides every context without a preferred context`() {
    val visibility = visibility(editorContexts = emptyList(), oneContextOnly = true, onlyContext = null)

    assertFalse(visibility.isVisible(a, analyzed))
    assertFalse(visibility.isVisible(b, analyzed))
  }

  private fun visibility(
    editorContexts: List<CodeInsightContext>,
    oneContextOnly: Boolean = false,
    onlyContext: CodeInsightContext? = editorContexts.firstOrNull(),
  ): ProblemsViewContextVisibility =
    ProblemsViewContextVisibility(editorContexts = editorContexts, oneContextOnly = oneContextOnly, onlyContext = onlyContext)
}
