// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics2D
import java.util.function.BiConsumer
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.exitProcess

fun sin() = lineChart<Int, Double> {

  datasets {
    dataset {
      lineColor = JBColor.BLUE
      stepped = LineStepped.NONE
      label = "Discrete (step = 1)"
      borderPainted = true
      generate {
        x = -360..360 step 1
        y = { sin(Math.toRadians(it.toDouble())) }
      }
    }
    dataset {
      lineColor = JBColor.BLACK
      stepped = LineStepped.NONE
      label = "Discrete (step = 45)"
      borderPainted = true
      generate {
        x = -360..360 step 45
        y = { sin(Math.toRadians(it.toDouble())) }
      }
    }
    dataset {
      lineColor = JBColor.RED
      stepped = LineStepped.NONE
      label = "Smooth (step = 45)"
      borderPainted = true
      smooth = true
      generate {
        x = -360..360 step 45
        y = { sin(Math.toRadians(it.toDouble())) }
      }
    }
    overlays = listOf(LabelOverlay())
    ranges {
      yMin = -1.25
      yMax = 1.25
    }
    grid {
      xLines = generator(90)
      xOrigin = 0
      xPainter {
        label = "%d".format(value)
        majorLine = value == 0
      }

      yLines = generator(0.25)
      yOrigin = 0.0
      yPainter {
        label = "%.2f".format(value)
        majorLine = value == 0.0
        horizontalAlignment = SwingConstants.RIGHT
        verticalAlignment = SwingConstants.TOP
      }
    }
  }

}

fun many() = lineChart<Double, Double> {
  dataset {
    generate {
      x = generator(1.0).prepare(0.0, 500_000.0)
      y = { x -> x.pow(3)  }
    }
  }
}

fun trivials() = lineChart<Double, Double> {
  val values = generator(0.01).prepare(0.0, 8.0)

  ranges {
    xMin = 0.0
    xMax = 8.0
    yMin = 0.0
    yMax = 8.0
  }
  datasets {
    dataset {
      generate {
        x = values
        y = 2.0::pow
      }
      lineColor = JBColor.GREEN
    }
    dataset {
      generate {
        x = values
        y = { it.pow(2.0) }
      }
      lineColor = JBColor.ORANGE
    }
    dataset {
      generate {
        x = values
        y = { it }
      }
      lineColor = JBColor.RED
    }
    dataset {
      generate {
        x = values
        y = { log2(it) * it }
      }
      lineColor = JBColor.BLUE
    }
    dataset {
      generate {
        x = values
        y = ::log2
      }
      lineColor = JBColor.MAGENTA
    }
  }
  grid {
    xLines = generator(1.0)
    yLines = generator(1.0)
  }
}

fun stepped() = lineChart<Int> {
  dataset {
    values = enumerator(1, 2, 1, 4, 3, 3, 2, 5, 3, 2, 5, 2, 1, 1, 2)
    stepped = LineStepped.AFTER
    fillColor = Color.GRAY
  }
  ranges {
    yMin = 0
    yMax = 6
  }
  grid {
    xLines = generator(1)
    yLines = generator(1)
  }
}

enum class YEAR {
  JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV, DEC
}

private fun simpleCategoryLineChart() = lineChart<Double> {

  margins {
    bottom = 100
  }
  datasets {
    dataset {
      values = listOf(1.0, 2.0, 3.0, 2.0, 3.0, 1.0, 1.0)
      lineColor = Color.BLUE
    }
    dataset {
      values = listOf(1.0, 2.0, 3.0, 2.0, 3.0, 1.0, 1.0)
      smooth = true
      lineColor = Color.RED
    }
  }
  grid {
    xLines = generator(1)
    xPainter {
      label = YEAR.values()[value].name
      horizontalAlignment = SwingUtilities.CENTER
    }
  }
}

private fun simpleXYLineChart() = lineChart<Double, Double> {
  datasets {
    dataset {
      data = listOf(1.0 to 1.0, 2.0 to 2.0, 2.5 to 3.0)
    }
    dataset {
      data = listOf(1.0 to 1.0, 2.0 to 2.0, 3.0 to 1.0)
      smooth = true
    }
  }
}

private fun funcXYLineChart() = lineChart<Double, Double> {
  datasets {
    // Generated values
    dataset {
      generate {
        x = listOf(0.0, 1.0, 2.0, 3.0)
        y = ::sin
      }
    }
    dataset {
      values {
        x = generator(0.1).prepare(0.0, 0.3)
        y = enumerator(0.0, 0.01, 0.002, 0.0003)
      }
    }
  }
}

fun areaChart() = lineChart<Int, Int> {
  val years = generator(5).prepare(1700, 1780).toList()
  margins {
    top = 40
    right = 40
    left = 40
    bottom = 30
  }
  ranges {
    yMin = 0
    yMax = 200
  }
  grid {
    xOrigin = 1780
    xLines = generator(10)
    xPainter {
      label = "%d".format(value)
      verticalAlignment = SwingConstants.BOTTOM
      horizontalAlignment = SwingConstants.CENTER
    }
    yLines = generator(10)
    yPainter {
      majorLine = value == 100
      label = "%d".format(value)
      verticalAlignment = SwingConstants.CENTER
      horizontalAlignment = SwingConstants.RIGHT
    }
  }
  overlays = listOf(
    object: Overlay<ChartWrapper>() {
      override fun paintComponent(g: Graphics2D) {
        g.color = JBColor.foreground()
        val str = "Exports and Imports to and from DENMARK & NORWAY from 1700 to 1780"
        val w = g.fontMetrics.stringWidth(str)
        g.drawString(str, (chart.width - w) / 2, 30)
      }
    }
  )
  datasets {
    dataset {
      stacked = true
      lineColor = Color(0, 0, 0, 0)
      smooth = true
      generate {
        x = years
        y = { min(get("Imports").find(it)!!, get("Exports").find(it)!!) }
      }
    }
    dataset {
      label = "Imports"
      lineColor = ColorUtil.fromHex("E7D8B6")
      fillColor = ColorUtil.fromHex("F8D9D8").transparent(0.5)
      smooth = true
      values {
        x = years
        y = listOf(70, 75, 80, 90, 100, 102, 98, 92, 92, 91, 90, 80, 77, 80, 85, 90, 90)
      }
    }
    dataset {
      label = "Exports"
      lineColor = ColorUtil.fromHex("F8D9D8")
      fillColor = ColorUtil.fromHex("E7D8B6").transparent(0.5)
      smooth = true
      values {
        x = years
        y = listOf(35, 43, 60, 80, 75, 70, 60, 60, 63, 72, 79, 80, 110, 150, 160, 180, 185, 190)
      }
    }
  }
}

fun zoom() = lineChart<Int, Double> {
  val values = 1..200
  val random = values.map { Random.nextInt(it).toDouble() }
  dataset {
    values {
      x = values
      y = random
    }
    grid {
      xMin = 1
      xMax = 200
      yMin = 0.0
      yMax = 200.0
    }
  }
  overlays = listOf(DragOverlay(BiConsumer { start, end ->
    val chart = this@lineChart
    val chartWidth = chart.width - chart.margins.left - chart.margins.right
    val chartHeight = chart.height - chart.margins.top - chart.margins.bottom
    val upperLeftX = min(start.x, end.x)
    val upperLeftY = min(start.y, end.y)
    val bottomRightX = max(start.x, end.x)
    val bottomRightY = max(start.y, end.y)

    val startXRatio = upperLeftX / chartWidth.toDouble()
    val startYRatio = upperLeftY / chartHeight.toDouble()
    val endXRatio = bottomRightX / chartWidth.toDouble()
    val endYRatio = bottomRightY / chartHeight.toDouble()

    chart.grid {
      val (xm, xx, ym, yx) = findMinMax()
      xMin = floor((xx - xm) * startXRatio + xm).toInt()
      xMax = ceil((xx - xm) * endXRatio + xm).toInt()
      yMax = (yx - ym) * (1 - startYRatio) + ym
      yMin = (yx - ym) * (1 - endYRatio) + ym
    }
    chart.update()
  }))
}

fun main() {
  UIManager.setLookAndFeel(DarculaLaf())
  SwingUtilities.invokeLater {
    object: DialogWrapper(false) {
      init {
        init()
        isModal = false
        setUndecorated(false)
        title = "Line Chart Kotlin Demo"
      }

      override fun createCenterPanel(): JComponent = JBTabbedPane().apply {
        preferredSize = Dimension(640, 480)
        tabPlacement = JTabbedPane.BOTTOM

        fun Component.setEmptyBorders() = this.apply {
          (this as JComponent).border = JBUI.Borders.empty()
        }

        add("Sin", sin().component).setEmptyBorders()
        add("Trivials", trivials().component).setEmptyBorders()
        add("Many", many().component).setEmptyBorders()
        add("Stepped", stepped().component).setEmptyBorders()
        add("Category", simpleCategoryLineChart().component).setEmptyBorders()
        add("XY", simpleXYLineChart().component).setEmptyBorders()
        add("Func", funcXYLineChart().component).setEmptyBorders()
        add("Import/Export", areaChart().component).setEmptyBorders()

        val zoomPane = BorderLayoutPanel()
        val zoom = zoom()
        zoomPane.addToCenter(zoom.component)
        val box = JBBox(BoxLayout.X_AXIS)
        box .add(JButton("Zoom in (+)").apply {
          addActionListener {
            val xChange = (zoom.grid.xMax - zoom.grid.xMin) / 4
            val yChange = (zoom.grid.yMax - zoom.grid.yMin) / 4
            zoom.grid.xMin += xChange
            zoom.grid.xMax -= xChange
            zoom.grid.yMin += yChange
            zoom.grid.yMax -= yChange
            zoom.update()
          }
        })
        box .add(JButton("Zoom out (-)").apply {
          addActionListener {
            val xChange = (zoom.grid.xMax - zoom.grid.xMin) / 2
            val yChange = (zoom.grid.yMax - zoom.grid.yMin) / 2
            zoom.grid.xMin -= xChange
            zoom.grid.xMax += xChange
            zoom.grid.yMin -= yChange
            zoom.grid.yMax += yChange
            zoom.update()
          }
        })
        box.add(JButton("Reset").apply {
          val (myXMin, myXMax, myYMin, myYMax) = zoom.grid
          addActionListener {
            zoom.grid {
              xMin = myXMin
              xMax = myXMax
              yMin = myYMin
              yMax = myYMax
            }
            zoom.update()
          }
        })
        box.alignmentX = 0.0f
        zoomPane.addToBottom(box)
        add("Zoomed", zoomPane).setEmptyBorders()
      }

      override fun createActions(): Array<Action> = arrayOf()

      override fun createSouthPanel(): JComponent? = null

      override fun createContentPaneBorder(): Border = JBUI.Borders.empty()

      override fun dispose() {
        super.disposeIfNeeded()
        exitProcess(0)
      }
    }.show()
  }
}