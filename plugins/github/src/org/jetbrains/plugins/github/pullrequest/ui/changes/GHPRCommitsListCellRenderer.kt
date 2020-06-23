// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.paint.PaintParameters
import org.jetbrains.plugins.github.api.data.GHCommit
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListCellRenderer

class GHPRCommitsListCellRenderer : ListCellRenderer<GHCommit> {

  private val nodeComponent = CommitNodeComponent().apply {
    foreground = DefaultColorGenerator().getColor(1)
  }
  private val messageComponent = SimpleColoredComponent()
  val panel = BorderLayoutPanel().addToLeft(nodeComponent).addToCenter(messageComponent)

  override fun getListCellRendererComponent(list: JList<out GHCommit>,
                                            value: GHCommit?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    messageComponent.clear()
    messageComponent.append(value?.messageHeadline.orEmpty(),
                            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, cellHasFocus)))
    SpeedSearchUtil.applySpeedSearchHighlighting(list, messageComponent, true, isSelected)

    val size = list.model.size
    when {
      size <= 1 -> nodeComponent.type = CommitNodeComponent.Type.SINGLE
      index == 0 -> nodeComponent.type = CommitNodeComponent.Type.FIRST
      index == size - 1 -> nodeComponent.type = CommitNodeComponent.Type.LAST
      else -> nodeComponent.type = CommitNodeComponent.Type.MIDDLE
    }
    panel.background = UIUtil.getListBackground(isSelected, cellHasFocus)
    return panel
  }

  private class CommitNodeComponent : JComponent() {

    var type = Type.SINGLE

    init {
      isOpaque = false
    }

    override fun getPreferredSize() = JBDimension(PaintParameters.getNodeWidth(PaintParameters.ROW_HEIGHT),
                                                  PaintParameters.ROW_HEIGHT)

    override fun paintComponent(g: Graphics) {
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)

      val g2 = g as Graphics2D

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      if (isOpaque) {
        g2.color = background
        g2.fill(Rectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat()))
      }

      g2.color = foreground
      drawNode(g2, rect)
      if (type == Type.LAST || type == Type.MIDDLE) {
        drawEdgeUp(g2, rect)
      }
      if (type == Type.FIRST || type == Type.MIDDLE) {
        drawEdgeDown(g2, rect)
      }
    }

    private fun drawNode(g: Graphics2D, rect: Rectangle) {
      val radius = PaintParameters.getCircleRadius(rect.height)
      val circle = Ellipse2D.Double(rect.centerX - radius, rect.centerY - radius, radius * 2.0, radius * 2.0)
      g.fill(circle)
    }

    private fun drawEdgeUp(g: Graphics2D, rect: Rectangle) {
      val y1 = 0.0
      val y2 = rect.centerY
      drawEdge(g, rect, y1, y2)
    }

    private fun drawEdgeDown(g: Graphics2D, rect: Rectangle) {
      val y1 = rect.centerY
      val y2 = rect.maxY
      drawEdge(g, rect, y1, y2)
    }

    private fun drawEdge(g: Graphics2D, rect: Rectangle, y1: Double, y2: Double) {
      val x = rect.centerX
      val width = PaintParameters.getLineThickness(rect.height)
      val line = Rectangle2D.Double(x - width / 2, y1 - 0.5, width.toDouble(), y1 + y2 + 0.5)
      g.fill(line)
    }

    enum class Type {
      SINGLE, FIRST, MIDDLE, LAST
    }
  }
}