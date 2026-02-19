// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import com.intellij.ide.ui.laf.setEarlyUiLaF
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.border.Border
import kotlin.math.cos
import kotlin.system.exitProcess

fun simple(): HorizontalBarChart<Double> = barChart {
  gap = 20
  datasets {
    dataset {
      values = enumerator(1.0, 2.0, 1.0, -1.0, 3.0, 2.0, 2.0)
      lineColor = Color(6, 128, 213)
      fillColor = Color(6, 128, 213, 122)
    }
    dataset {
      values = enumerator(1.5, 1.0, .5, -2.0, 2.0, 5.0, 1.0)
      stacked = true
      lineColor = Color(213, 6, 13)
      fillColor = Color(213, 6, 13, 122)
    }
    dataset {
      values = enumerator(2.0, 3.0, 1.5, -.20, 2.5, 1.0, 0.0)
      lineColor = Color(56, 158, 38)
      fillColor = Color(56, 158, 38, 122)
    }
    dataset {
      values = generate {
        count = 7
        function = { i -> i - count / 2.toDouble()}
      }
    }
  }
  grid {
    xLines = generator(1)
    yLines = generator(1.0)
    yOrigin = 0.0
    xPainter {
      label = "Test"
    }
  }
}

fun performance(): HorizontalBarChart<Double> = barChart {
  val format: (Double) -> String = { v -> "%.2f".format(v) }
  val labels = listOf("macOS Mojave 10.14.6", "Ubuntu 16.04", "Windows 10")
  ranges {
    yMax = 15.0
    yMin = 0.0
  }
  space = 1
  gap = 80
  margins {
    top = 30
    bottom = 40
    left = 40
    right = 40
  }
  datasets {
    dataset {
      showValues = format
      label = "2019.1"
      values = enumerator(8.57, 10.8, 12.53)
      lineColor = null
      fillColor = ColorUtil.fromHex("999999")
    }
    dataset {
      showValues = format
      label = "2019.2"
      values = enumerator(7.14, 10.0, 10.42)
      lineColor = null
      fillColor = ColorUtil.fromHex("262525")
    }
    dataset {
      showValues = format
      label = "2019.3"
      lineColor = null
      values = enumerator(5.42, 6.24, 7.18)
      fillColor = ColorUtil.fromHex("1255CC")
    }
  }
  grid {
    yLines = generator(5.0)
    yPainter {
      label = "%.0fs".format(value)
      majorLine = 0.0 == value
      horizontalAlignment = SwingUtilities.LEFT
      verticalAlignment = SwingUtilities.CENTER
    }
    xLines = generator(1)
    xPainter {
      label = labels[value]
      paintLine = false
    }
    xOrigin = 0
  }
}

fun generate() = barChart<Float> {
  dataset {
    values = generate {
      count = 180
      function =  { i -> cos(Math.toRadians(i.toDouble())).toFloat() }
    }
  }
}


fun main() {
  setEarlyUiLaF()
  SwingUtilities.invokeLater {
    object: DialogWrapper(false) {
      init {
        init()
        isModal = false
        setUndecorated(false)
        title = "Bar Chart Kotlin Demo"
      }

      override fun createCenterPanel(): JComponent = JBTabbedPane().apply {
        preferredSize = Dimension(640, 480)
        tabPlacement = JTabbedPane.BOTTOM


        add("Performance", performance().component).apply { (this as JComponent).border = JBUI.Borders.empty() }
        add("Chart", simple().component).apply { (this as JComponent).border = JBUI.Borders.empty() }
        add("Generate", generate().component).apply { (this as JComponent).border = JBUI.Borders.empty() }
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