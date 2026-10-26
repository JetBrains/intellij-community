// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
