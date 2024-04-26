// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import kotlin.math.max
import kotlin.math.min

internal class ScrollPositionCalculatorImpl : ScrollPositionCalculator {
  override fun getHorizontalOffset(editor: Editor,
                                   targetLocation: Point,
                                   scrollType: ScrollType,
                                   viewRect: Rectangle,
                                   scrollPane: JScrollPane): Int {
    var horizontalOffset = viewRect.x
    val spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor)
    val editorWidth = viewRect.width
    val scrollWidth = scrollPane.horizontalScrollBar.maximum - scrollPane.horizontalScrollBar.getExtent()
    val textWidth = scrollWidth + editorWidth
    val scrollOffset = editor.settings.horizontalScrollOffset
    val scrollJump = editor.settings.horizontalScrollJump

    // when we calculate bounds, we assume that characters have the same width (spaceWidth),
    // it's not the most accurate way to handle side scroll offset, but definitely the fastest
    //
    // text between these two following bounds should be visible in view rectangle after scrolling
    // (that's the meaning of the scroll offset setting)
    // if it is not possible e.g. view rectangle is too small to contain the whole range,
    // then scrolling will center the targetLocation
    val leftBound = targetLocation.x - scrollOffset * spaceWidth
    val rightBound = targetLocation.x + scrollOffset * spaceWidth
    if (rightBound - leftBound > editorWidth) { // if editor width is not enough to satisfy offsets from both sides, we center target location
      horizontalOffset = targetLocation.x - editorWidth / 2
    }
    else if (leftBound < viewRect.x) {
      horizontalOffset = if (scrollType == ScrollType.MAKE_VISIBLE && rightBound < editorWidth) {
        // here we try to scroll to 0, if it is possible (that's the point of MAKE_VISIBLE)
        0
      }
      else {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a narrow editor width).
        val leftmostPossibleLocation = getLeftmostLocation(targetLocation, editorWidth, scrollOffset, spaceWidth)
        val leftAfterScrollJump = max(leftmostPossibleLocation, viewRect.x - scrollJump * spaceWidth)
        min(leftBound, leftAfterScrollJump)
      }
    }
    else if (rightBound > viewRect.x + editorWidth) {
      val rightmostPossibleLocation = getRightmostLocation(targetLocation, textWidth, editorWidth, scrollOffset, spaceWidth)
      // this is done to ensure safety in cases where scrolling excessively may obscure the target location
      // (due to a large scroll jump or a narrow editor width).
      val rightAfterScrollJump = min(rightmostPossibleLocation, viewRect.x + editorWidth + scrollJump * spaceWidth)
      horizontalOffset = max(rightBound, rightAfterScrollJump) - editorWidth
    }
    horizontalOffset = max(0, horizontalOffset)
    horizontalOffset = min(scrollWidth, horizontalOffset)
    return horizontalOffset
  }

  /**
   * Gets the upmost possible y-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the bottom of targetLocation to contain offsetBottomBound
   */
  private fun getTopmostLocation(targetLocation: Point, editorHeight: Int, offsetBottomBound: Int): Int {
    val topmostLocation = targetLocation.y - editorHeight
    return if (topmostLocation < 0) {
      0
    }
    else topmostLocation + (offsetBottomBound - targetLocation.y)
  }

  /**
   * Gets the bottommost possible y-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the top of targetLocation to contain offsetTopBound
   */
  private fun getBottommostLocation(targetLocation: Point, textHeight: Int, editorHeight: Int, offsetTopBound: Int): Int {
    val bottommostLocation = targetLocation.y + editorHeight
    return if (bottommostLocation > textHeight) {
      textHeight
    }
    else bottommostLocation - (targetLocation.y - offsetTopBound)
  }

  /**
   * Gets the leftmost possible x-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the right of targetLocation to satisfy the scroll offset
   */
  private fun getLeftmostLocation(targetLocation: Point, editorWidth: Int, scrollOffset: Int, spaceWidth: Int): Int {
    val leftmostLocation = targetLocation.x - editorWidth
    return if (leftmostLocation < 0) {
      0
    }
    else leftmostLocation + scrollOffset * spaceWidth
  }

  /**
   * Gets the rightmost possible x-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the left of targetLocation to satisfy the scroll offset
   */
  private fun getRightmostLocation(targetLocation: Point, textWidth: Int, editorWidth: Int, scrollOffset: Int, spaceWidth: Int): Int {
    val rightmostLocation = targetLocation.x + editorWidth
    return if (rightmostLocation > textWidth) {
      textWidth
    }
    else rightmostLocation - scrollOffset * spaceWidth
  }

  override fun getVerticalOffset(editor: Editor,
                                 targetLocation: Point,
                                 scrollType: ScrollType,
                                 viewRect: Rectangle,
                                 scrollPane: JScrollPane): Int {
    val editorHeight = viewRect.height
    val lineHeight = editor.lineHeight
    val scrollHeight = scrollPane.verticalScrollBar.maximum - scrollPane.verticalScrollBar.getExtent()
    val textHeight = scrollHeight + editorHeight
    val scrollOffset = editor.settings.verticalScrollOffset
    val scrollJump = editor.settings.verticalScrollJump
    // the two following lines should be both visible in view rectangle after scrolling (that's the meaning of the scroll offset setting)
    //  If it is not possible, e.g., view rectangle is too small to contain both lines, then scrolling will go to the `centerPosition`
    val offsetTopBound = addVerticalOffsetToPosition(editor, -scrollOffset, targetLocation)
    val offsetBottomBound = addVerticalOffsetToPosition(editor, scrollOffset, targetLocation) + lineHeight

    // the position that we consider to be the "central" one
    // for some historical reasons, before scroll offset support, center was actually at the 1/3 of the view rectangle
    val oneThirdPosition = targetLocation.y - editorHeight / 3
    val centerPosition = if (oneThirdPosition < offsetTopBound) { // if editor has enough height to show top bound, let the center be in its historical (expected for users) position
      oneThirdPosition
    }
    else { // the real centered position for ones who use big offsets or don't have enough height
      targetLocation.y - max(0, viewRect.height - lineHeight) / 2
    }
    var verticalOffset = viewRect.y
    if (scrollType == ScrollType.CENTER) {
      verticalOffset = centerPosition
    }
    else if (scrollType == ScrollType.CENTER_UP) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound || viewRect.y > centerPosition) {
        verticalOffset = centerPosition
      }
    }
    else if (scrollType == ScrollType.CENTER_DOWN) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound || viewRect.y < centerPosition) {
        verticalOffset = centerPosition
      }
    }
    else if (scrollType == ScrollType.RELATIVE) {
      if (offsetBottomBound - offsetTopBound > editorHeight) {
        verticalOffset = centerPosition
      }
      else if (viewRect.y + viewRect.height < offsetBottomBound) {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a small editor height).
        val bottomAfterScrollJump = min(
          getBottommostLocation(targetLocation, textHeight, editorHeight, offsetTopBound),
          addVerticalOffsetToPosition(editor, scrollJump, Point(viewRect.x, viewRect.y + viewRect.height))
        )
        verticalOffset = max(offsetBottomBound - viewRect.height, bottomAfterScrollJump - viewRect.height)
      }
      else if (viewRect.y > offsetTopBound) {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a small editor height).
        val topAfterScrollJump = max(
          getTopmostLocation(targetLocation, editorHeight, offsetBottomBound),
          addVerticalOffsetToPosition(editor, -scrollJump, Point(viewRect.x, viewRect.y))
        )
        verticalOffset = min(offsetTopBound, topAfterScrollJump)
      }
    }
    else if (scrollType == ScrollType.MAKE_VISIBLE) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound) {
        verticalOffset = centerPosition
      }
    }
    verticalOffset = max(0, verticalOffset)
    verticalOffset = min(scrollHeight, verticalOffset)
    return verticalOffset
  }


  /**
   * @param editor target editor
   * @param scrollOffset scroll offset value. Should be positive for bottom scroll bound and negative for top scroll bound
   * @param point target point that we are scrolling to
   * @return y-coordinate of the line obtained by adding scroll offset to the given point
   */
  private fun addVerticalOffsetToPosition(editor: Editor, scrollOffset: Int, point: Point): Int {
    val isUseSoftWraps = editor.settings.isUseSoftWraps
    return if (scrollOffset > 0) {
      // if we are calculating the bottom scroll bound, add scroll offset to the end of logical line containing the given point,
      // because we want to see some following lines and not just wrapped visual lines of the same logical line
      val lastVisualLine = getLastVisualLine(editor, point, isUseSoftWraps)
      val bottomLine = lastVisualLine + scrollOffset
      editor.visualLineToY(bottomLine)
    }
    else if (scrollOffset < 0) {
      // we count offset from logical line start to see previous lines (not the same line content if soft wrap is enabled)
      val topVisualLine = getFirstVisualLine(editor, point, isUseSoftWraps)
      var topLineWithOffset = max(0, topVisualLine + scrollOffset)
      // If soft wraps are enabled, last visual lines of one logical line may be not so helpful. That's why we scroll to logical line start
      topLineWithOffset = getFirstVisualLine(editor, VisualPosition(topLineWithOffset, 0), isUseSoftWraps)
      editor.visualLineToY(topLineWithOffset)
    }
    else {
      point.y
    }
  }

  private fun getLastVisualLine(editor: Editor, point: Point, isUseSoftWraps: Boolean): Int {
    if (isUseSoftWraps) {
      val logicalPosition = editor.xyToLogicalPosition(point)
      val lineCount = editor.document.lineCount
      if (logicalPosition.line < lineCount) {
        val endOffset = editor.document.getLineEndOffset(logicalPosition.line)
        return editor.offsetToVisualPosition(endOffset).line
      }
    }
    return editor.yToVisualLine(point.y)
  }

  private fun getFirstVisualLine(editor: Editor, point: Point, isUseSoftWraps: Boolean): Int {
    return if (isUseSoftWraps) {
      val logicalLine = editor.xyToLogicalPosition(point).line
      editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0)).line
    }
    else {
      editor.yToVisualLine(point.y)
    }
  }

  private fun getFirstVisualLine(editor: Editor, visualPosition: VisualPosition, isUseSoftWraps: Boolean): Int {
    return if (isUseSoftWraps) {
      val logicalPosition = editor.visualToLogicalPosition(visualPosition)
      editor.logicalToVisualPosition(LogicalPosition(logicalPosition.line, 0)).line
    }
    else {
      visualPosition.line
    }
  }

  private fun JScrollBar.getExtent(): Int {
    return this.model?.extent ?: 0
  }
}