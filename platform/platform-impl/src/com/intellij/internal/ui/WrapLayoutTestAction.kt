// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class WrapLayoutTestAction : DumbAwareAction("WrapLayout Demo") {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      init {
        title = "WrapLayout Demo"
        isResizable = true
        init()
      }

      override fun createCenterPanel(): JComponent {
        val panel = JPanel(WrapLayout(FlowLayout.LEADING, JBUI.scale(50), JBUI.scale(50)))
        (0..6).forEach { i ->
          panel.add(createSquareComponent(i))
        }
        panel.background = JBColor.WHITE
        panel.preferredSize = JBDimension(501, 501)
        panel.isFocusable = true
        return panel
      }
    }.show()
  }

  private fun createSquareComponent(i: Int): JComponent {
    val square = JLabel("" + (i+1), SwingConstants.CENTER)
    square.background = ColorUtil.darker(JBColor.GREEN, System.identityHashCode(square) % 8)
    square.isOpaque = true
    square.minimumSize = JBDimension(100, 100)
    square.maximumSize = JBDimension(100, 100)
    square.preferredSize = JBDimension(100, 100)
    return square
  }
}