// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint

import com.intellij.ui.JBColor
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.ParityMode.ODD
import com.intellij.ui.paint.PaintUtil.RoundingMode.FLOOR
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.SmartList
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle
import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.NodePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * @author erokhins
 */
@ApiStatus.Internal
open class SimpleGraphCellPainter(private val colorGenerator: ColorGenerator) : GraphCellPainter {
  protected open val rowHeight: Int get() = PaintParameters.ROW_HEIGHT

  override fun paint(g2: Graphics2D, commitStyle: VcsCommitStyle, printElements: Collection<PrintElement>) {
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val painter = MyPainter(ScaleContext.create(g2), rowHeight)
    val selected: MutableList<PrintElement> = SmartList()
    for (printElement in printElements) {
      if (printElement.isSelected) {
        selected.add(printElement) // to draw later
      }
      else {
        painter.paintElement(g2, printElement, commitStyle, colorGenerator.getColor(printElement.colorId), false)
      }
    }

    // draw selected elements
    for (printElement in selected) {
      painter.paintElement(g2, printElement, commitStyle, MARK_COLOR, true)
    }

    for (printElement in selected) {
      painter.paintElement(g2, printElement, commitStyle, colorGenerator.getColor(printElement.colorId), false)
    }
  }

  override fun getElementUnderCursor(scaleContext: ScaleContext, printElements: Collection<PrintElement>, x: Int, y: Int): PrintElement? {
    val painter = MyPainter(scaleContext, rowHeight)
    for (printElement in printElements) {
      if (printElement is NodePrintElement) {
        if (painter.isOverNode(printElement.positionInCurrentRow, x, y)) {
          return printElement
        }
      }
    }

    for (printElement in printElements) {
      if (printElement is EdgePrintElement) {
        if (painter.isOverEdge(printElement.positionInCurrentRow, printElement.positionInOtherRow, printElement.type, x, y)) {
          return printElement
        }
      }
    }
    return null
  }

  private class MyPainter(scaleContext: ScaleContext, private val rowHeight: Int) {
    private val pixel = 1 / PaintUtil.devValue(1.0, scaleContext)
    private val rowCenter = PaintUtil.alignToInt(rowHeight / 2.0, scaleContext, FLOOR, ODD)

    private val elementWidth = PaintUtil.alignToInt(PaintParameters.getElementWidth(rowHeight), scaleContext, FLOOR, ODD)
    private val elementCenter = PaintUtil.alignToInt(PaintParameters.getElementWidth(rowHeight) / 2, scaleContext, FLOOR, null)

    private val lineThickness = PaintUtil.alignToInt(PaintParameters.getLineThickness(rowHeight), scaleContext, FLOOR, ODD)
      .coerceAtLeast(pixel).toFloat()
    private val selectedLineThickness = PaintUtil.alignToInt(PaintParameters.getSelectedLineThickness(rowHeight), scaleContext, FLOOR, ODD)
      .coerceAtLeast(lineThickness + 2 * pixel).toFloat()

    private val circleDiameter = PaintUtil.alignToInt(2 * PaintParameters.getCircleRadius(rowHeight), scaleContext, FLOOR, ODD)
    private val selectedCircleDiameter = circleDiameter + selectedLineThickness - lineThickness
    private val circleRadius = PaintUtil.alignToInt(circleDiameter / 2, scaleContext, FLOOR, null)
    private val selectedCircleRadius = PaintUtil.alignToInt(selectedCircleDiameter / 2, scaleContext, FLOOR, null)

    private val ordinaryStroke = BasicStroke(lineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    private val selectedStroke = BasicStroke(selectedLineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    private val headNodePainter = HeadNodePainter(scaleContext, selectedLineThickness, lineThickness, rowHeight)

    private fun getDashedStroke(dash: FloatArray): Stroke {
      return BasicStroke(lineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, dash,
                         dash[0] / 2)
    }

    private fun getSelectedDashedStroke(dash: FloatArray): Stroke {
      return BasicStroke(selectedLineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, dash,
                         dash[0] / 2)
    }

    fun isOverEdge(position: Int, otherPosition: Int, edgeType: EdgePrintElement.Type, x: Int, y: Int): Boolean {
      val x1 = elementWidth * position + elementCenter
      val y1 = rowCenter
      val x2 = elementWidth * otherPosition + elementCenter
      val y2 = if (edgeType == EdgePrintElement.Type.DOWN) rowHeight + rowCenter else rowCenter - rowHeight
      return hypot(x1 - x, y1 - y) + hypot(x2 - x, y2 - y) < hypot(x1 - x2, y1 - y2) + lineThickness
    }

    fun isOverNode(position: Int, x: Int, y: Int): Boolean {
      val x0 = elementWidth * position + elementCenter
      val y0 = rowCenter
      return hypot(x0 - x, y0 - y) <= circleRadius
    }

    private fun paintLine(g2: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double, startArrowX: Double, startArrowY: Double,
                          hasArrow: Boolean, isUsual: Boolean, isSelected: Boolean) {
      g2.stroke = if (isUsual || hasArrow) {
        if (isSelected) selectedStroke else ordinaryStroke
      }
      else {
        val edgeLength = if (x1 == x2) rowHeight.toDouble() else hypot((x1 - x2), (y1 - y2))
        val dashLength = getDashLength(edgeLength, rowHeight)
        if (isSelected) getSelectedDashedStroke(dashLength) else getDashedStroke(dashLength)
      }

      g2.draw(Line2D.Double(x1, y1, x2, y2))
      if (hasArrow) {
        val (endArrowX1, endArrowY1) = rotate(x1, y1, startArrowX, startArrowY, sqrt(ARROW_ANGLE_COS2), sqrt(1 - ARROW_ANGLE_COS2),
                                              ARROW_LENGTH * rowHeight)
        val (endArrowX2, endArrowY2) = rotate(x1, y1, startArrowX, startArrowY, sqrt(ARROW_ANGLE_COS2), -sqrt(1 - ARROW_ANGLE_COS2),
                                              ARROW_LENGTH * rowHeight)
        g2.draw(Line2D.Double(startArrowX, startArrowY, endArrowX1, endArrowY1))
        g2.draw(Line2D.Double(startArrowX, startArrowY, endArrowX2, endArrowY2))
      }
    }

    private fun paintEdge(g2: Graphics2D, edge: EdgePrintElement, isSelected: Boolean) {
      val isDown = edge.type == EdgePrintElement.Type.DOWN
      val isUsual = edge.lineStyle == EdgePrintElement.LineStyle.SOLID
      val hasArrow = edge.hasArrow()
      val isTerminal = edge is TerminalEdgePrintElement

      val from = edge.positionInCurrentRow
      val to = edge.positionInOtherRow

      val x1 = elementWidth * from + elementCenter
      val y1 = rowCenter
      if (from == to) {
        val arrowGap = if (isTerminal) circleRadius / 2 + 1 else 0.0
        val y2 = if (isDown) rowHeight - arrowGap else arrowGap
        paintLine(g2, x1, y1, x1, y2, x1, y2, hasArrow, isUsual, isSelected)
      }
      else {
        assert(!isTerminal)
        // paint non-vertical lines twice the size to make them dock with each other well
        val x2 = elementWidth * to + elementCenter
        val y2 = if (isDown) rowHeight + rowCenter else rowCenter - rowHeight
        paintLine(g2, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2, hasArrow, isUsual, isSelected)
      }
    }

    private fun paintCircle(g2: Graphics2D, element: NodePrintElement, isSelected: Boolean, commitStyle: VcsCommitStyle) {
      val x0 = elementWidth * element.positionInCurrentRow + elementCenter
      val y0 = rowCenter
      val radius = if (isSelected) selectedCircleRadius else circleRadius
      val diameter = if (isSelected) selectedCircleDiameter else circleDiameter

      val circle = Ellipse2D.Double(x0 - radius, y0 - radius, diameter, diameter)
      when (element.nodeType) {
        NodePrintElement.Type.FILL -> g2.fill(circle)
        NodePrintElement.Type.OUTLINE_AND_FILL -> headNodePainter.paint(g2, x0, y0, isSelected, commitStyle)
        NodePrintElement.Type.OUTLINE -> {
          g2.color = NEUTRAL_COLOR
          g2.draw(circle)
        }
      }
    }

    fun paintElement(g2: Graphics2D, element: PrintElement, commitStyle: VcsCommitStyle, color: Color, isSelected: Boolean) {
      g2.color = color
      when (element) {
        is EdgePrintElement -> paintEdge(g2, element, isSelected)
        is NodePrintElement -> paintCircle(g2, element, isSelected, commitStyle)
      }
    }
  }

  companion object {
    private val MARK_COLOR: Color = JBColor.BLACK
    private val NEUTRAL_COLOR: Color = JBColor.GRAY
    private const val ARROW_ANGLE_COS2 = 0.7
    private const val ARROW_LENGTH = 0.3

    private fun rotate(x: Double, y: Double, centerX: Double, centerY: Double, cos: Double, sin: Double,
                       arrowLength: Double): Pair<Double, Double> {
      val translateX = (x - centerX)
      val translateY = (y - centerY)

      val d = hypot(translateX, translateY)
      val scaleX = arrowLength * translateX / d
      val scaleY = arrowLength * translateY / d

      val rotateX = scaleX * cos - scaleY * sin
      val rotateY = scaleX * sin + scaleY * cos

      return Pair(rotateX + centerX, rotateY + centerY)
    }

    private fun getDashLength(edgeLength: Double, rowHeight: Int): FloatArray {
      // If the edge is vertical, then edgeLength is equal to rowHeight. Exactly one dash and one space fits on the edge,
      // so spaceLength + dashLength is also equal to rowHeight.
      // When the edge is not vertical, spaceLength is kept the same, but dashLength is chosen to be slightly greater
      // so that the whole number of dashes would fit on the edge.

      val dashCount = max(1, floor(edgeLength / rowHeight).toInt())
      val spaceLength = rowHeight / 2.0f - 2
      val dashLength = (edgeLength / dashCount - spaceLength).toFloat()
      return floatArrayOf(dashLength, spaceLength)
    }
  }
}
