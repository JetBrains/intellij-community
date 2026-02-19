// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.BiConsumer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class LabelOverlay: Overlay<LineChart<*, *, *>>() {
  override fun paintComponent(g: Graphics2D) {
    val bounds = g.fontMetrics.getStringBounds("SAMPLE", null)
    g.stroke = BasicStroke(5F)
    chart.datasets.forEachIndexed { i, dataset ->
      g.paint = dataset.lineColor
      val y = chart.height - (bounds.height + i * bounds.height).toInt()
      g.drawLine(10, y, 30, y)
      if (dataset.label != null) {
        val text = dataset.label!!
        g.drawString(text, 35, y + (bounds.height / 3).roundToInt())
      }
    }
  }
}

class DragOverlay(val onRelease: BiConsumer<Point, Point>): Overlay<LineChart<*, *, *>>() {

  private var startPoint: Point? = null

  override fun afterChartInitialized() {
    chart.component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        startPoint = e.point.toChartSpace()
      }
      override fun mouseReleased(e: MouseEvent) {
        val start = startPoint
        val end = e.point.toChartSpace()
        startPoint = null
        if (start != null && end != null)
          onRelease.accept(start, end)
      }
    })
  }

  override fun paintComponent(g: Graphics2D) {
    val start = startPoint
    if (start == null) {
      return
    }
    val end = mouseLocation
    if (end == null) {
      return
    }

    g.color = JBColor.BLACK
    g.drawRect(min(start.x, end.x), min(start.y, end.y), abs(start.x - end.x), abs(start.y - end.y))

  }

}