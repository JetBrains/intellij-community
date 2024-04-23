// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.util.DocumentUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Point
import java.awt.Rectangle
import kotlin.collections.HashSet

@Internal
class VisualStickyLines(
  private val editor: Editor,
  private val stickyModel: StickyLinesModel,
  private val scopeMinSize: Int,
) {
  constructor(editor: Editor, stickyModel: StickyLinesModel)
    : this(editor, stickyModel, scopeMinSize = 5)

  init {
    require(scopeMinSize >= 2)
  }

  private val logicalStickyLines: MutableList<StickyLine> = mutableListOf()
  private val visualStickyLines: MutableList<VisualStickyLine> = mutableListOf()
  private val visualWithYStickyLines: MutableList<VisualStickyLine> = mutableListOf()

  private var totalHeight: Int = 0
  private var lineHeight: Int = 0
  private var lineLimit: Int = 0

  fun recalculate(visibleArea: Rectangle) {
    clear()
    recalculate(visibleArea, logicalStickyLines, visualStickyLines)
  }

  fun lines(visibleArea: Rectangle): List<VisualStickyLine> {
    visualWithYStickyLines.clear()
    totalHeight = setYLocation(visibleArea, visualStickyLines, visualWithYStickyLines)
    return visualWithYStickyLines
  }

  fun height(): Int {
    return totalHeight
  }

  fun clear() {
    logicalStickyLines.clear()
    visualStickyLines.clear()
    visualWithYStickyLines.clear()
    totalHeight = 0
  }

  private fun recalculate(
    visibleArea: Rectangle,
    logicalLines: MutableList<StickyLine>,
    visualLines: MutableList<VisualStickyLine>,
  ) {
    lineLimit = editor.settings.stickyLinesLimit
    lineHeight = editor.lineHeight
    collectLogical(visibleArea, logicalLines)
    logicalToVisualLines(logicalLines, visualLines)
  }

  private fun collectLogical(
    visibleArea: Rectangle,
    logicalStickyLines: MutableList<StickyLine>,
  ) {
    val maxStickyPanelHeight: Int = lineHeight * lineLimit + /*border*/ 1
    val yStart: Int = visibleArea.y
    val yEnd: Int = yStart + maxStickyPanelHeight
    val startLine: Int = editor.xyToLogicalPosition(Point(0, yStart)).line
    var endLine: Int = editor.xyToLogicalPosition(Point(0, yEnd)).line
    if (!DocumentUtil.isValidLine(endLine, editor.document)) {
      if (!DocumentUtil.isValidLine(startLine, editor.document)) {
        return
      }
      endLine = editor.document.lineCount - 1
    }
    val startOffset: Int = editor.document.getLineStartOffset(startLine)
    val endOffset: Int = editor.document.getLineEndOffset(endLine)
    stickyModel.processStickyLines(startOffset, endOffset) { stickyLine ->
      logicalStickyLines.add(stickyLine)
      true
    }
  }

  private fun logicalToVisualLines(
    logicalLines: MutableList<StickyLine>,
    visualLines: MutableList<VisualStickyLine>,
  ) {
    if (logicalLines.isEmpty()) {
      return
    }
    val deduplicatedLines: MutableSet<Int> = HashSet()
    for (logicalLine: StickyLine in logicalLines) {
      val primaryVisual: Int = toVisualLine(logicalLine.primaryLine())
      val isNotDuplicate: Boolean = deduplicatedLines.add(primaryVisual)
      if (isNotDuplicate) {
        val scopeVisual: Int = toVisualLine(logicalLine.scopeLine())
        if (isScopeNotNarrow(primaryVisual, scopeVisual)) {
          visualLines.add(VisualStickyLine(
            logicalLine,
            primaryVisual,
            scopeVisual,
          ))
        }
      }
    }
    visualLines.sort()
  }

  private fun setYLocation(
    visibleArea: Rectangle,
    visualLines: List<VisualStickyLine>,
    withYLocation: MutableList<VisualStickyLine>,
  ): Int {
    if (visualLines.isEmpty()) {
      return 0
    }
    var totalPanelHeight = 0
    val editorY: Int = visibleArea.y
    val editorH: Int = visibleArea.height
    if (isPanelTooBig(lineHeight, totalPanelHeight, editorH)) {
      return 0
    }
    for (line: VisualStickyLine in visualLines) {
      val startY1: Int = editor.visualLineToY(line.primaryLine())
      val startY2: Int = startY1 + lineHeight
      val endY1:   Int = editor.visualLineToY(line.scopeLine())
      val endY2:   Int = endY1 + lineHeight
      val stickyY: Int = editorY + totalPanelHeight + lineHeight
      if (startY2 < stickyY && stickyY <= endY2) {
        val yOverlap: Int = if (stickyY <= endY1) 0 else stickyY - endY1
        assert(yOverlap >= 0) {
          "startY1: $startY1, startY2: $startY2, endY1: $endY1, endY2: $endY2, stickyY: $stickyY"
        }
        line.yLocation = totalPanelHeight - yOverlap
        totalPanelHeight += lineHeight - yOverlap
        if (lineHeight > yOverlap) {
          withYLocation.add(line)
        }
        if (yOverlap > 0 ||
            withYLocation.size >= lineLimit ||
            isPanelTooBig(lineHeight, totalPanelHeight, editorH)) {
          break
        }
      }
    }
    return totalPanelHeight
  }

  private fun toVisualLine(logicalLine: Int): Int {
    return editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0)).line
  }

  private fun isPanelTooBig(lineHeight: Int, panelHeight: Int, editorHeight: Int): Boolean {
    return panelHeight + 2 * lineHeight > editorHeight / 2
  }

  private fun isScopeNotNarrow(primaryVisual: Int, scopeVisual: Int): Boolean {
    return scopeVisual - primaryVisual + 1 >= scopeMinSize
  }
}
