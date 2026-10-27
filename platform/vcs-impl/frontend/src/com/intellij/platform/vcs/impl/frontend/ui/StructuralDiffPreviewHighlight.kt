// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffDividerDrawUtil
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.highlightStructuralPreview
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent

/**
 * Dims a diff viewer outside the lines of one hunk. The unified viewer dims one range that holds the removed and the added lines; the
 * side-by-side viewer dims each side and the divider, and a side without lines of the hunk gets the dim on all its lines. The gutters get
 * the dim too. The dim comes back after each rediff and goes away on mouse or keyboard input in the viewer.
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
    repaintOnDimStrengthChange(viewer.component, this)
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
    return listOfNotNull(dim(viewer.getEditor(Side.LEFT), lines.base), dim(viewer.getEditor(Side.RIGHT), lines.current), dimDivider(viewer))
  }

  /** Dims the divider of the side-by-side viewer outside the link of the hunk. The divider of the platform paints first. */
  private fun dimDivider(viewer: TwosideTextDiffViewer): Disposable? {
    val splitter = UIUtil.findComponentOfType(viewer.component, DiffSplitter::class.java) ?: return null
    val original = splitter.painter
    val painter = DimmedDividerPainter(original, viewer, lines)
    splitter.painter = painter
    splitter.repaintDivider()
    return Disposable {
      if (splitter.painter === painter) splitter.painter = original
      splitter.repaintDivider()
    }
  }

  /** Dims the editor and its gutter outside the lines. A side without lines of the hunk gets the dim on all its lines. */
  private fun dim(editor: Editor, range: LineRange): Disposable? {
    if (range.end > editor.document.lineCount) return null
    val lines = if (range.isEmpty) null else DiffUtil.getLinesRange(editor.document, range.start, range.end)
    return highlightStructuralPreview(editor, lines, wholeLines = true, gutter = true)
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

/**
 * Paints the divider of the platform, and then the dim outside the link between the two sides of the hunk. The dim is an image, and the
 * platform paints the shape of the link into it in the destination-out mode. So the hole in the dim has the exact shape of the link.
 */
private class DimmedDividerPainter(
  private val original: DiffSplitter.Painter?,
  private val viewer: TwosideTextDiffViewer,
  private val lines: ChangesBrowserStructuralNode.DiffLines,
) : DiffSplitter.Painter {
  override fun paint(g: Graphics, divider: JComponent) {
    original?.paint(g, divider)
    val left: Editor = viewer.getEditor(Side.LEFT)
    val right: Editor = viewer.getEditor(Side.RIGHT)
    if (left.isDisposed || right.isDisposed || !divider.isShowing || !left.component.isShowing) return
    if (lines.base.end > left.document.lineCount || lines.current.end > right.document.lineCount) return
    val graphics = DiffDividerDrawUtil.getDividerGraphics(g, divider, left.component)
    try {
      val clip = graphics.clipBounds ?: Rectangle(0, 0, divider.width, left.component.height)
      if (clip.isEmpty) return
      val dim = ImageUtil.createImage(graphics, clip.width, clip.height, BufferedImage.TYPE_INT_ARGB)
      val dimGraphics = dim.createGraphics()
      try {
        dimGraphics.translate(-clip.x, -clip.y)
        dimGraphics.color = left.colorsScheme.defaultBackground
        GraphicsUtil.paintWithAlpha(dimGraphics, StructuralChangesViewSettings.previewDimAlpha()) { dimGraphics.fill(clip) }
        dimGraphics.composite = AlphaComposite.DstOut
        GraphicsUtil.setupAAPainting(dimGraphics)
        link(left, right).paint(dimGraphics, divider.width, /* curve = */ true)
      }
      finally {
        dimGraphics.dispose()
      }
      StartupUiUtil.drawImage(graphics, dim, clip.x, clip.y, null)
    }
    finally {
      graphics.dispose()
    }
  }

  /** The link between the lines of the hunk on both sides, at the place where the divider of the platform paints it. */
  private fun link(left: Editor, right: Editor): DiffDividerDrawUtil.DividerPolygon {
    val leftRange = DiffDrawUtil.getGutterMarkerPaintRange(left, lines.base.start, lines.base.end)
    val rightRange = DiffDrawUtil.getGutterMarkerPaintRange(right, lines.current.start, lines.current.end)
    val leftOffset = DiffDividerDrawUtil.getEditorTopOffset(left)
    val rightOffset = DiffDividerDrawUtil.getEditorTopOffset(right)
    return DiffDividerDrawUtil.DividerPolygon(leftRange.y1 + leftOffset, rightRange.y1 + rightOffset, leftRange.y2 + leftOffset,
                                              rightRange.y2 + rightOffset, JBColor.BLACK, /* borderColor = */ null,
                                              /* dottedBorder = */ false)
  }
}
