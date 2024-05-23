// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.ui.ColorUtil
import com.intellij.util.DocumentUtil
import java.awt.Point
import java.awt.Rectangle

/**
 * Responsible for updating state of the sticky panel e.g., when the editor is scrolled or resized.
 */
internal class StickyLinesManager(
  private val editor: EditorEx,
  markupModel: MarkupModelEx,
  parentDisposable: Disposable,
) : VisibleAreaListener, Disposable, StickyLinesModel.Listener {

  private val stickyModel: StickyLinesModel = StickyLinesModel.getModel(markupModel)
  private val shadowPainter: StickyLineShadowPainter = StickyLineShadowPainter(isDarkColorScheme())
  val stickyPanel: StickyLinesPanel = StickyLinesPanel(editor, shadowPainter)

  init {
    Disposer.register(parentDisposable, this)
    editor.scrollingModel.addVisibleAreaListener(this, this)
    stickyModel.addListener(this)
    editor.project!!.messageBus.connect(this).subscribe(
      UISettingsListener.TOPIC,
      UISettingsListener {
        shadowPainter.isDarkColorScheme = isDarkColorScheme()
        recalculateLinesAndRepaint()
      }
    )
  }

  private var activeVisualLine: Int = -1
  private var activeEditorY: Int = -1
  private var activeEditorH: Int = -1

  override fun visibleAreaChanged(event: VisibleAreaEvent) {
    if (editor.settings.areStickyLinesShown() && isAreaChanged(event)) {
      activeEditorY = event.newRectangle.y
      activeEditorH = event.newRectangle.height
      if (activeEditorY <3) {
        // special case when the document starts with a sticky line
        // small visual jump is better than stickied line for good
        activeVisualLine = -1
        stickyPanel.repaintLines(activeEditorY, activeEditorH, newStickyLines = emptyList())
      } else if (event.oldRectangle == null || isLineChanged(activeEditorY)) {
        // recalculate sticky lines and repaint
        stickyPanel.repaintLines(activeEditorY, activeEditorH, getStickyLines(activeEditorY))
      } else if (isYChanged(event) || isSizeChanged(event)) {
        // just repaint
        stickyPanel.repaintLines(activeEditorY, activeEditorH)
      }
    }
  }

  override fun modelChanged() {
    recalculateLinesAndRepaint()
  }

  override fun dispose() {
    stickyModel.removeListener(this)
  }

  private fun isDarkColorScheme(): Boolean {
    val background = editor.colorsScheme.defaultBackground
    return ColorUtil.isDark(background)
  }

  private fun recalculateLinesAndRepaint() {
    if (activeVisualLine != -1 && activeEditorY != -1 && activeEditorH != -1) {
      stickyPanel.repaintLines(activeEditorY, activeEditorH, getStickyLines(activeEditorY))
    }
  }

  private fun isAreaChanged(event: VisibleAreaEvent): Boolean {
    val oldRectangle: Rectangle? = event.oldRectangle
    return oldRectangle == null ||
           oldRectangle.y != event.newRectangle.y ||
           oldRectangle.height != event.newRectangle.height ||
           oldRectangle.width != event.newRectangle.width
  }

  private fun isLineChanged(editorY: Int): Boolean {
    val newVisualLine: Int = editor.yToVisualLine(editorY)
    if (activeVisualLine != newVisualLine) {
      activeVisualLine = newVisualLine
      return true
    }
    return false
  }

  private fun isYChanged(event: VisibleAreaEvent): Boolean {
    return event.oldRectangle.y != event.newRectangle.y
  }

  private fun isSizeChanged(event: VisibleAreaEvent): Boolean {
    return event.oldRectangle.width != event.newRectangle.width ||
           event.oldRectangle.height != event.newRectangle.height
  }

  private fun getStickyLines(editorY: Int): List<StickyLine> {
    val stickyLines: List<StickyLine>? = collectStickyLines(editorY)
    if (stickyLines == null) {
      // IDEA-344327 editorY may become invalid while Git update
      activeVisualLine = -1
      return emptyList()
    }
    return stickyLines
  }

  private fun collectStickyLines(editorY: Int): List<StickyLine>? {
    val maxStickyPanelHeight: Int = editor.lineHeight * editor.settings.stickyLinesLimit + /*border*/ 1
    val stickyPanelRange: TextRange = yRangeToOffsetRange(
      yStart = editorY,
      yEnd = editorY + maxStickyPanelHeight
    ) ?: return null
    return collectStickyLines(stickyPanelRange)
  }

  private fun yRangeToOffsetRange(yStart: Int, yEnd: Int): TextRange? {
    val startLine: Int = editor.xyToLogicalPosition(Point(0, yStart)).line
    val endLine: Int = editor.xyToLogicalPosition(Point(0, yEnd)).line
    if (DocumentUtil.isValidLine(endLine, editor.document)) {
      val startOffset: Int = editor.document.getLineStartOffset(startLine)
      val endOffset: Int = editor.document.getLineEndOffset(endLine)
      return TextRange(startOffset, endOffset)
    }
    return null
  }

  private fun collectStickyLines(stickyPanelRange: TextRange): List<StickyLine> {
    val visualLinesLimit: Int = editor.settings.stickyLinesLimit
    // The panel removes visual duplicates so extra logical lines should be collected.
    // We could collect lines limitless here if it were efficient.
    // The consequence of this optimization is that in the worst case (when all logical lines are the same visual one),
    // the sticky panel will consist of only one line instead of a line limit number.
    // This optimization should be removed if the worst case is not rare.
    val logicalLinesLimit: Int = (1.7 * visualLinesLimit).toInt()
    val stickyLines: MutableList<StickyLine> = ArrayList(logicalLinesLimit)
    stickyModel.processStickyLines(stickyPanelRange.endOffset) { stickyLine ->
      if (stickyLine.textRange().intersects(stickyPanelRange)) {
        stickyLines.add(stickyLine)
      }
      return@processStickyLines stickyLines.size < logicalLinesLimit
    }
    return stickyLines.sorted()
  }
}
