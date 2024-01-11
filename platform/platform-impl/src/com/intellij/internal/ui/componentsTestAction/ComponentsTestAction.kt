// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.internal.ui.createListSelectionPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

internal class ComponentsTestAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ComponentsTestDialog(e.project).apply {
      title = e.presentation.text
      show()
    }
  }

  private class ComponentsTestDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, false) {

    init {
      init()
    }

    override fun createCenterPanel(): JComponent {
      val data = mapOf("JBOptionButton" to JBOptionButtonPanel().panel,
                       "JButton" to JButtonPanel().panel,
                       "JTextField" to JTextFieldPanel().panel,
                       "JComboBox" to JComboBoxPanel().panel,
                       "JSpinner" to JSpinnerPanel().panel,
                       "JCheckBox" to JCheckBoxPanel().panel,
                       "JRadioButton" to JRadioButtonPanel().panel,
                       "ThreeStateCheckBoxPanel" to ThreeStateCheckBoxPanel().panel)

      val result = createListSelectionPanel(data, "ComponentsTestAction.splitter.proportion")
      result.preferredSize = JBDimension(800, 600)

      return result
    }
  }
}
