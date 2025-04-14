// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.statusBar

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class XNextStatusBarWidgetPane {
  private val widgetPanel = JPanel(HorizontalLayout(10, SwingConstants.CENTER)).apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, 10)
    minimumSize = JBDimension(JBUI.scale(300), minimumSize.height)
  }

  val component: JComponent = widgetPanel
  fun init(project: Project) {
    InternalUICustomization.getInstance()?.progressWidget(project)?.let {
      widgetPanel.add(it)
    }

    val ideMessagePanel = IdeMessagePanel(null, MessagePool.getInstance())
    widgetPanel.add(ideMessagePanel.component)
  }
}