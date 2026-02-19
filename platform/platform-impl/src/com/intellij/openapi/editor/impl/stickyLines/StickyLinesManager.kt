// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLineColors
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLinesPanel
import com.intellij.openapi.util.Disposer
import java.awt.Rectangle


internal class StickyLinesManager(
  private val editor: Editor,
  private val stickyModel: StickyLinesModel,
  private val stickyPanel: StickyLinesPanel,
  private val stickyColors: StickyLineColors,
  private val visualStickyLines: VisualStickyLines,
  parentDisposable: Disposable,
) : VisibleAreaListener, StickyLinesModel.Listener, Disposable {

  private var activeVisualArea: Rectangle = Rectangle()
  private var activeVisualLine: Int = -1
  private var activeLineHeight: Int = -1
  private var activeIsEnabled: Boolean = false
  private var activeLineLimit: Int = -1

  init {
    Disposer.register(parentDisposable, this)
    editor.scrollingModel.addVisibleAreaListener(this, this)
    stickyModel.addListener(this)
  }

  fun repaintLines(startVisualLine: Int, endVisualLine: Int) {
    stickyPanel.repaintLines(startVisualLine, endVisualLine)
  }

  fun panelHeight(): Int {
    return visualStickyLines.height()
  }

  fun startDumb() {
    stickyPanel.startDumb()
  }

  fun suppressHintForLine(logicalLine: Int): Boolean {
    for (line: VisualStickyLine in visualStickyLines.lines(activeVisualArea)) {
      val stickyVisualPos = VisualPosition(line.primaryLine(), 0)
      val stickyLogicalLine: Int = editor.visualToLogicalPosition(stickyVisualPos).line
      if (logicalLine == stickyLogicalLine ||
          logicalLine == stickyLogicalLine - 1 ||
          logicalLine == stickyLogicalLine + 1) {
        return true
      }
    }
    return false
  }

  fun reinitSettings() {
    val oldIsEnabled: Boolean = activeIsEnabled
    val newIsEnabled: Boolean = editor.settings.areStickyLinesShown()
    val oldLineLimit: Int = activeLineLimit
    val newLineLimit: Int = editor.settings.stickyLinesLimit
    val colorsChanged: Boolean = stickyColors.updateScheme(editor.colorsScheme)

    activeIsEnabled = newIsEnabled
    activeLineLimit = newLineLimit

    if (newIsEnabled && !oldIsEnabled) {
      recalculateAndRepaintLines(force = true)
    } else if (!newIsEnabled && oldIsEnabled) {
      resetLines()
    } else if (newLineLimit != oldLineLimit) {
      recalculateAndRepaintLines()
    } else if (colorsChanged) {
      repaintLines()
    }
  }

  fun clearStickyModel() {
    stickyModel.removeAllStickyLines(editor.project!!)
  }

  override fun visibleAreaChanged(event: VisibleAreaEvent) {
    if (editor.settings.areStickyLinesShown() && isAreaChanged(event)) {
      activeVisualArea = event.newRectangle
      if (activeVisualArea.y < 3) {
        // special case when the document starts with a sticky line
        // small visual jump is better than stickied line for good
        resetLines()
      } else if (event.oldRectangle == null || isLineChanged()) {
        recalculateAndRepaintLines()
      } else if (isYChanged(event) || isSizeChanged(event)) {
        repaintLines()
      }
    }
  }

  override fun linesUpdated() {
    recalculateAndRepaintLines()
  }

  override fun dispose() {
    stickyModel.removeListener(this)
  }

  private fun recalculateAndRepaintLines(force: Boolean = false) {
    if (force) {
      activeVisualArea = editor.scrollingModel.visibleArea
      isLineChanged() // activeVisualLine updated as a side effect
    }
    if (activeVisualLine != -1 && activeLineHeight != -1 && !isPoint(activeVisualArea)) {
      visualStickyLines.recalculate(activeVisualArea)
      repaintLines()
    }
  }

  private fun resetLines() {
    activeVisualLine = -1
    activeLineHeight = -1
    visualStickyLines.clear()
    repaintLines()
  }

  private fun repaintLines() {
    stickyPanel.repaintLines()
  }

  private fun isAreaChanged(event: VisibleAreaEvent): Boolean {
    val oldRectangle: Rectangle? = event.oldRectangle
    return oldRectangle == null ||
           oldRectangle.y != event.newRectangle.y ||
           oldRectangle.height != event.newRectangle.height ||
           oldRectangle.width != event.newRectangle.width
  }

  private fun isLineChanged(): Boolean {
    val newVisualLine: Int = editor.yToVisualLine(activeVisualArea.y)
    val newLineHeight: Int = editor.lineHeight
    if (activeVisualLine != newVisualLine || activeLineHeight != newLineHeight) {
      activeVisualLine = newVisualLine; activeLineHeight = newLineHeight
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

  private fun isPoint(rectangle: Rectangle): Boolean {
    return rectangle.x == 0 &&
           rectangle.y == 0 &&
           rectangle.height == 0 &&
           rectangle.width == 0
  }
}
