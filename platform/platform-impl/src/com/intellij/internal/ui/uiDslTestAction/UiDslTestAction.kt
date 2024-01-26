// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.internal.ui.createListSelectionPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent
import javax.swing.border.Border

internal class UiDslTestAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    UiDslTestDialog(e.project).apply {
      title = e.presentation.text
      show()
    }
  }
}

@Suppress("DialogTitleCapitalization")
private class UiDslTestDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, false) {

  init {
    init()
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  override fun createCenterPanel(): JComponent {
    val data = mapOf("Segmented Button" to SegmentedButtonPanel(myDisposable).panel,
                     "Placeholder" to PlaceholderPanel(myDisposable).panel,
                     "Validation Refactoring API" to ValidationRefactoringPanel(myDisposable).panel,
                     "OnChange" to OnChangePanel().panel)

    val listSelectionPanel = createListSelectionPanel(data, "UiDslTestAction.splitter.proportion")
    listSelectionPanel.preferredSize = JBDimension(800, 600)

    return panel {
      row {
        button("Long texts") {
          LongTextsDialog().show()
        }
      }

      row {
        cell(listSelectionPanel)
          .align(Align.FILL)
      }.resizableRow()
    }
  }
}
