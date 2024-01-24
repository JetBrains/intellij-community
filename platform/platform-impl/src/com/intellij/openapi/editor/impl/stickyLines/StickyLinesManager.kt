// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.util.Disposer
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
  val stickyPanel: StickyLinesPanel = StickyLinesPanel(editor)

  init {
    Disposer.register(parentDisposable, this)
    editor.scrollingModel.addVisibleAreaListener(this, this)
    stickyModel.addListener(this)
    editor.project!!.messageBus.connect(this).subscribe(
      UISettingsListener.TOPIC,
      UISettingsListener { recalculateLinesAndRepaint() }
    )
  }

  private var activeVisualLine: Int = -1
  private var activeEditorY: Int = -1
  private var activeEditorH: Int = -1

  override fun visibleAreaChanged(event: VisibleAreaEvent) {
    if (editor.settings.areStickyLinesShown() && isAreaChanged(event)) {
      activeEditorY = event.newRectangle.y
      activeEditorH = event.newRectangle.height
      if (event.oldRectangle == null || isLineChanged(event)) {
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

  private fun isLineChanged(event: VisibleAreaEvent): Boolean {
    val editorY: Int = event.newRectangle.y
    val newVisualLine: Int = editor.yToVisualLine(activeY(editorY))
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

  private fun activeY(editorY: Int): Int {
    return editorY + stickyPanel.height + /*border*/ 1
  }

  private fun getStickyLines(editorY: Int): List<StickyLine> {
    return if (editorY <3) {
      // special case when the document starts with a sticky line
      // small visual jump is better than stickied line for good
      emptyList()
    } else {
      val activeY: Int = activeY(editorY)
      val activeLogicalLine: Int = editor.xyToLogicalPosition(Point(0, activeY)).line
      val activeOffset: Int = editor.document.getLineEndOffset(activeLogicalLine)
      collectStickyLines(activeOffset, activeLogicalLine)
    }
  }

  private fun collectStickyLines(activeOffset: Int, activeLogicalLine: Int): List<StickyLine> {
    val visualLinesLimit: Int = editor.settings.stickyLinesLimit
    // The panel removes visual duplicates so extra logical lines should be collected.
    // We could collect lines limitless here if it were efficient.
    // The consequence of this optimization is that in the worst case (when all logical lines are the same visual one),
    // the sticky panel will consist of only one line instead of a line limit number.
    // This optimization should be removed if the worst case is not rare.
    val logicalLinesLimit: Int = visualLinesLimit + 5
    val stickyLines: MutableList<StickyLine> = ArrayList(logicalLinesLimit)
    stickyModel.processStickyLines(activeOffset) { stickyLine ->
      if (activeLogicalLine <= stickyLine.scopeLine()) {
        stickyLines.add(stickyLine)
      }
      return@processStickyLines stickyLines.size < logicalLinesLimit
    }
    return stickyLines.sorted()
  }
}
