// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

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
import javax.swing.JPanel

@Suppress("HardCodedStringLiteral")
class WrapLayoutTestAction : DumbAwareAction("WrapLayout Demo") {
  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      init {
        title = "WrapLayout Demo"
        isResizable = true
        init()
      }

      override fun createCenterPanel(): JComponent {
        val panel = JPanel(WrapLayout(FlowLayout.LEADING, JBUI.scale(50), JBUI.scale(50)))
        (0..5).forEach { i ->
          panel.add(createSquareComponent())
        }
        panel.background = JBColor.WHITE
        panel.preferredSize = JBDimension(501, 501)
        panel.isFocusable = true
        return panel
      }
    }.show()
  }
  private fun createSquareComponent():JComponent {
    val square = JPanel()
    square.background = ColorUtil.darker(JBColor.GREEN, System.identityHashCode(square) % 8)
    square.minimumSize = JBDimension(100, 100)
    square.maximumSize = JBDimension(100, 100)
    square.preferredSize = JBDimension(100, 100)
    return square
  }
}