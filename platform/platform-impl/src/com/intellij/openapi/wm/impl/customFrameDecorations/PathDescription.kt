// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import sun.swing.SwingUtilities2
import java.awt.*
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.UIManager


class PathDescription(disposable: Disposable) {
  companion object {
    const val fileSeparatorChar = '/'
    const val ellipsisSymbol = "\u2026"
  }

  private var clippedText: String? = null
    set(value) {
      if (value == field) return
      field = value
      label.revalidate()
      label.repaint()
    }

  private val label = object : JLabel() {
    override fun paint(g: Graphics?) {
      g as Graphics2D

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setColor(UIManager.getColor("Label.disabledForeground"))
      val h = height - insets.top - insets.bottom

      SwingUtilities2.drawStringUnderlineCharAt(this, g, clippedText, -1, insets.left, getBaseline(width, h))
    }
  }.apply {
    isEnabled = false

  }

  fun getView(): JComponent = this.label

  fun getListenerBoundses(): List<Rectangle> {
    val mouseInsets = 2
    val projectLabelRect = label.bounds

    return if(clippedText.equals(label.text)) {
      emptyList()
    } else {
      listOf(
        Rectangle(projectLabelRect.x, projectLabelRect.y, mouseInsets, projectLabelRect.height),
        Rectangle(projectLabelRect.x, projectLabelRect.y, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.x, projectLabelRect.maxY.toInt() - mouseInsets, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.maxX.toInt() - mouseInsets, projectLabelRect.y, mouseInsets, projectLabelRect.height)
      )
    }
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      update()
    }
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
          update()
        }
      })
    this.label.addComponentListener(resizedListener)
    Disposer.register(disposable, Disposable { this.label.removeComponentListener(resizedListener) })
  }

  private fun update() {
    val path = getProjectPath()
    label.text = path
    clippedText = path?.let {
      val fm = label.getFontMetrics(label.font)

      val insets = label.getInsets(null)
      val width: Int = label.width - (insets.right + insets.left)

      val textWidth = SwingUtilities2.stringWidth(label, fm, path)

      if (textWidth > width) {
        label.toolTipText = path
        clipString(label, path, width)
      }
      else {
        label.toolTipText = null
        path
      }
    }
  }

  private fun getProjectPath(): String? {
    return guessCurrentProject(this.label).basePath
  }

  private fun clipString(component: JComponent, string: String, maxWidth: Int): String {
    val fm = component.getFontMetrics(component.font)
    val symbolWidth = SwingUtilities2.stringWidth(component, fm, ellipsisSymbol)
    return when {
      symbolWidth > maxWidth -> ""
      symbolWidth == maxWidth -> ellipsisSymbol
      else -> {

        val availTextWidth = maxWidth - symbolWidth

        val separate = string.split(fileSeparatorChar)
        var str = ""
        var stringWidth = 0
        for (i in separate.lastIndex downTo 1) {
          stringWidth += SwingUtilities2.stringWidth(component, fm, separate[i] + fileSeparatorChar)
          if (stringWidth <= availTextWidth) {
            str = fileSeparatorChar + separate[i] + str
          }

        }

        return ellipsisSymbol + str
      }
    }
  }

}