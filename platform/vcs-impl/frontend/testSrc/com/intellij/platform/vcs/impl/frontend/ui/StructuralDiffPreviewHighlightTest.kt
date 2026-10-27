// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.swing.JComponent

/** The dim of a hunk in the side-by-side viewer. */
@TestApplication
internal class StructuralDiffPreviewHighlightTest {
  @Test
  fun `a hunk without lines on one side dims both sides`(): Unit = timeoutRunBlocking {
    withViewer(base = "first\nremoved\nlast", current = "first\nlast") { viewer ->
      val editors = Side.entries.map { viewer.getEditor(it) }
      val before = editors.map(::customRenderers)
      // The hunk removes the second line, so the current side has no line of it.
      val highlight = StructuralDiffPreviewHighlight(viewer, ChangesBrowserStructuralNode.DiffLines(LineRange(1, 2), LineRange(1, 1)))
      highlight.apply(scroll = false)
      assertEquals(before.map { it + 1 }, editors.map(::customRenderers), "Each side gets one dim")
      Disposer.dispose(highlight)
    }
  }

  @Test
  fun `the dim covers the gutters of both sides`(): Unit = timeoutRunBlocking {
    withViewer(base = "first\nold\nlast", current = "first\nnew\nlast") { viewer ->
      val gutters = Side.entries.map<Side, Editor> { viewer.getEditor(it) }.map { it.gutter as JComponent }
      val before = gutters.map { it.componentCount }
      val highlight = StructuralDiffPreviewHighlight(viewer, ChangesBrowserStructuralNode.DiffLines(LineRange(1, 2), LineRange(1, 2)))
      highlight.apply(scroll = false)
      assertEquals(before.map { it + 1 }, gutters.map { it.componentCount }, "Each gutter gets one dim")
      Disposer.dispose(highlight)
      assertEquals(before, gutters.map { it.componentCount }, "The end of the dim removes it from the gutters")
    }
  }

  @Test
  fun `the preview moves both carets to the hunk`(): Unit = timeoutRunBlocking {
    withViewer(base = "first\nsecond\nold\nlast", current = "first\nsecond\nnew\nlast") { viewer ->
      val editors = Side.entries.map<Side, Editor> { viewer.getEditor(it) }
      for (editor in editors) editor.caretModel.moveToOffset(0)
      val highlight = StructuralDiffPreviewHighlight(viewer, ChangesBrowserStructuralNode.DiffLines(LineRange(2, 3), LineRange(2, 3)))
      highlight.apply(scroll = true)
      assertEquals(listOf(2, 2), editors.map { it.caretModel.logicalPosition.line })
      Disposer.dispose(highlight)
    }
    withViewer(base = "first\nremoved\nlast", current = "first\nlast") { viewer ->
      val editors = Side.entries.map<Side, Editor> { viewer.getEditor(it) }
      for (editor in editors) editor.caretModel.moveToOffset(0)
      val highlight = StructuralDiffPreviewHighlight(viewer, ChangesBrowserStructuralNode.DiffLines(LineRange(1, 2), LineRange(1, 1)))
      highlight.apply(scroll = true)
      assertEquals(listOf(1, 1), editors.map { it.caretModel.logicalPosition.line },
                   "The side without lines gets the caret after the change")
      Disposer.dispose(highlight)
    }
  }

  @Test
  fun `the dim of the divider ends with the highlight`(): Unit = timeoutRunBlocking {
    withViewer(base = "first\nold\nlast", current = "first\nnew\nlast") { viewer ->
      val splitter = checkNotNull(UIUtil.findComponentOfType(viewer.component, DiffSplitter::class.java))
      val platformPainter = checkNotNull(splitter.painter)
      val highlight = StructuralDiffPreviewHighlight(viewer, ChangesBrowserStructuralNode.DiffLines(LineRange(1, 2), LineRange(1, 2)))
      highlight.apply(scroll = false)
      assertNotSame(platformPainter, splitter.painter, "The dim paints over the divider")
      // A rediff applies the dim again.
      highlight.apply(scroll = false)
      Disposer.dispose(highlight)
      assertSame(platformPainter, splitter.painter, "The divider gets its own painter back")
    }
  }

  private fun customRenderers(editor: Editor): Int = editor.markupModel.allHighlighters.count { it.customRenderer != null }

  private suspend fun withViewer(base: String, current: String, test: (SimpleDiffViewer) -> Unit) {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      val contents = DiffContentFactory.getInstance()
      val request = SimpleDiffRequest(null, contents.create(project, base), contents.create(project, current), null, null)
      val viewer = SimpleDiffViewer(TestDiffContext(project), request)
      try {
        viewer.init()
        PlatformTestUtil.waitWithEventsDispatching("The rediff did not end", { !viewer.hasPendingRediff() }, 10)
        test(viewer)
      }
      finally {
        Disposer.dispose(viewer)
      }
    }
  }

  private class TestDiffContext(private val project: Project) : DiffContext() {
    override fun getProject(): Project = project
    override fun isWindowFocused(): Boolean = true
    override fun isFocusedInWindow(): Boolean = true
    override fun requestFocusInWindow() {}
  }

  private companion object {
    val projectFixture = projectFixture()
  }
}
