// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

internal class ComponentsTestAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ComponentsTestDialog(e.project).show()
  }

  private class ComponentsTestDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, false) {

    init {
      title = "Components Tests"
      init()
    }

    override fun createCenterPanel(): JComponent {
      val tabbedPane = JBTabbedPane()
      tabbedPane.minimumSize = JBDimension(300, 200)
      tabbedPane.preferredSize = JBDimension(1000, 800)
      tabbedPane.addTab("JBOptionButton", JBOptionButtonPanel().panel)
      tabbedPane.addTab("ComboBox", ComboBoxPanel().panel)
      return tabbedPane
    }
  }
}