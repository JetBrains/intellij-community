// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.highlightStructuralPreview
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/**
 * Dims a diff viewer outside the lines of one hunk. The unified viewer dims one range that holds the removed and the added lines; the
 * side-by-side viewer dims each side. The dim comes back after each rediff and goes away on mouse or keyboard input in the viewer.
 */
internal class StructuralDiffPreviewHighlight(
  private val viewer: DiffViewerBase,
  private val lines: ChangesBrowserStructuralNode.DiffLines,
) : Disposable {
  private var markups: List<Disposable> = emptyList()
  private val rediffListener = object : DiffViewerListener() {
    override fun onAfterRediff() {
      apply(scroll = false)
    }
  }

  init {
    viewer.addListener(rediffListener)
    Disposer.register(viewer, this)
    for (editor in editors()) {
      editor.addEditorMouseListener(object : EditorMouseListener {
        override fun mousePressed(event: EditorMouseEvent) = Disposer.dispose(this@StructuralDiffPreviewHighlight)
      }, this)
      val keyListener = object : KeyAdapter() {
        override fun keyPressed(event: KeyEvent) = Disposer.dispose(this@StructuralDiffPreviewHighlight)
      }
      editor.contentComponent.addKeyListener(keyListener)
      Disposer.register(this) { editor.contentComponent.removeKeyListener(keyListener) }
    }
  }

  /** Dims the viewer outside the hunk. With [scroll], it also moves the caret to the hunk and brings the hunk into view. */
  @RequiresEdt
  fun apply(scroll: Boolean) {
    clearMarkups()
    markups = when (viewer) {
      is UnifiedDiffViewer -> applyUnified(viewer, scroll)
      is TwosideTextDiffViewer -> applyTwoside(viewer, scroll)
      else -> emptyList()
    }
  }

  private fun applyUnified(viewer: UnifiedDiffViewer, scroll: Boolean): List<Disposable> {
    // The unified document shows the removed lines of a hunk before its added lines.
    val start = if (lines.base.isEmpty) viewer.transferLineToOnesideStrict(Side.RIGHT, lines.current.start)
    else viewer.transferLineToOnesideStrict(Side.LEFT, lines.base.start)
    val last = if (lines.current.isEmpty) viewer.transferLineToOnesideStrict(Side.LEFT, lines.base.end - 1)
    else viewer.transferLineToOnesideStrict(Side.RIGHT, lines.current.end - 1)
    if (start !in 0..last) return emptyList()
    val editor = viewer.editor
    if (scroll) scrollTo(editor, LineRange(start, last + 1))
    return listOfNotNull(dim(editor, LineRange(start, last + 1)))
  }

  private fun applyTwoside(viewer: TwosideTextDiffViewer, scroll: Boolean): List<Disposable> {
    if (scroll) {
      if (lines.current.isEmpty) scrollTo(viewer.getEditor(Side.LEFT), lines.base)
      else scrollTo(viewer.getEditor(Side.RIGHT), lines.current)
    }
    return listOfNotNull(dim(viewer.getEditor(Side.LEFT), lines.base), dim(viewer.getEditor(Side.RIGHT), lines.current))
  }

  private fun dim(editor: Editor, range: LineRange): Disposable? {
    if (range.isEmpty || range.end > editor.document.lineCount) return null
    return highlightStructuralPreview(editor, DiffUtil.getLinesRange(editor.document, range.start, range.end))
  }

  /** Centers a hunk that fits the viewport. A longer hunk starts near the top. */
  private fun scrollTo(editor: Editor, range: LineRange) {
    if (range.isEmpty || range.start >= editor.document.lineCount) return
    editor.caretModel.moveToLogicalPosition(LogicalPosition(range.start, 0))
    val visibleArea = editor.scrollingModel.visibleArea
    val top = editor.logicalPositionToXY(LogicalPosition(range.start, 0)).y
    val bottom = editor.logicalPositionToXY(LogicalPosition(range.end, 0)).y
    val target = if (bottom - top < visibleArea.height) (top + bottom - visibleArea.height) / 2 else top - 2 * editor.lineHeight
    editor.scrollingModel.scrollVertically(target.coerceAtLeast(0))
  }

  private fun editors(): List<Editor> = when (viewer) {
    is UnifiedDiffViewer -> listOf(viewer.editor)
    is TwosideTextDiffViewer -> viewer.editors
    else -> emptyList()
  }

  private fun clearMarkups() {
    markups.forEach(Disposer::dispose)
    markups = emptyList()
  }

  override fun dispose() {
    viewer.removeListener(rediffListener)
    clearMarkups()
  }
}
