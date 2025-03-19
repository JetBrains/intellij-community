// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.statusBar

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.diagnostic.MessagePool
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class XNextStatusBarWidgetPane {
  private val widgetPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, 10)
    minimumSize = JBDimension(JBUI.scale(300), minimumSize.height)
  }

  val component: JComponent = widgetPanel
  init {
    val ideMessagePanel = IdeMessagePanel(null, MessagePool.getInstance())
    widgetPanel.add(ideMessagePanel.component,BorderLayout.EAST)
  }
}