// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.statusBar

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.flow.collectLatest
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class XNextStatusBarWidgetPane {
  private val widgetPanel = JPanel(GridBagLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, 10)
    minimumSize = JBDimension(JBUI.scale(300), minimumSize.height)
  }

  val component: JComponent = widgetPanel

  fun init(project: Project) {
    val constraints = GridBagConstraints().apply {
      gridx = 1
      gridy = 0
      weightx = 0.0
      fill = GridBagConstraints.NONE
    }

    val messagePanel = IdeMessagePanel(null, MessagePool.getInstance())
    widgetPanel.add(messagePanel.component, constraints)
    val showInEditor = ProgressPlaceChecker.getInstance().showInEditor

    InternalUICustomization.getInstance()?.progressWidget(project)?.let { pw ->
      widgetPanel.launchOnShow("XNextStatusBarProgressWidget") {
        showInEditor.collectLatest {
          if (!it) {
            constraints.insets = JBUI.insetsRight(10)
            constraints.gridx = 0
            widgetPanel.add(pw, constraints)
          }
          else {
            widgetPanel.remove(pw)
            widgetPanel.revalidate()
            widgetPanel.repaint()
          }
        }
      }
    }
  }
}
