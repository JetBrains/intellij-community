// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.toolbarLayout.RIGHT_ALIGN_KEY
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.impl.headertoolbar.FilenameToolbarWidgetAction
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetAction
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("HardCodedStringLiteral")
@Internal
internal class ShowCompressibleToolbarAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent): Unit = BundleMessagesDialog(e.project).show()

  private class BundleMessagesDialog(project: Project?) : DialogWrapper(project) {
    init {
      init()
    }

    override fun createCenterPanel(): JComponent {
      val panel1 = JPanel()
      panel1.setLayout(BoxLayout(panel1, BoxLayout.X_AXIS))
      panel1.add(createLabel())
      panel1.add(createToolbar())
      panel1.add(createLabel())

      val panel2 = JPanel()
      panel2.setLayout(BoxLayout(panel2, BoxLayout.X_AXIS))
      panel2.add(createLabel())
      panel2.add(createToolbar())
      panel2.add(createToolbar())

      val panel3 = JPanel()
      panel3.setLayout(BoxLayout(panel3, BoxLayout.X_AXIS))
      panel3.add(createToolbar())
      panel3.add(createToolbar())
      panel3.add(createLabel())

      val panel4 = JPanel()
      panel4.setLayout(BoxLayout(panel4, BoxLayout.X_AXIS))
      panel4.add(createToolbar())
      panel4.add(createToolbar())

      val mainPanel = JPanel()
      mainPanel.setLayout(BoxLayout(mainPanel, BoxLayout.Y_AXIS))
      mainPanel.preferredSize = Dimension(730, 150)
      mainPanel.add(panel1)
      mainPanel.add(panel2)
      mainPanel.add(panel3)
      mainPanel.add(panel4)
      return mainPanel
    }

    fun createToolbar(): JComponent {
      val actionGroup = DefaultActionGroup()
      actionGroup.addAction(object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
      })
      actionGroup.addAction(ProjectToolbarWidgetAction())
      actionGroup.addAction(ProjectToolbarWidgetAction())
      actionGroup.addAction(FilenameToolbarWidgetAction())
      actionGroup.addAction(RightAlignedAction())
      val actionToolbar = ActionManager.getInstance().createActionToolbar("CompressibleToolbar", actionGroup, true)
      actionToolbar.layoutStrategy = ToolbarLayoutStrategy.COMPRESSING_STRATEGY
      return actionToolbar.component
    }

    fun createLabel(): JComponent {
      return JLabel("Outside toolbar text")
    }

  }

  private class RightAlignedAction : AnAction(), CustomComponentAction {
    override fun actionPerformed(e: AnActionEvent) {
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return JLabel("Right aligned").apply {
        putClientProperty(RIGHT_ALIGN_KEY, true)
      }
    }
  }
}