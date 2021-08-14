// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.Cell
import com.intellij.ui.dsl.RowLayout
import com.intellij.ui.dsl.columns
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.panel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.Border


class UiDslDemoAction : DumbAwareAction("Show UI DSL Demo") {

  override fun actionPerformed(e: AnActionEvent) {
    UiDslDemoDialog(e.project).show()
  }
}

@Suppress("DialogTitleCapitalization")
private class UiDslDemoDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, false) {

  init {
    title = "UI DSL Demo"
    init()
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  override fun createCenterPanel(): JComponent {
    val result = JBTabbedPane()
    result.minimumSize = Dimension(300, 200)
    result.preferredSize = Dimension(800, 600)
    result.addTab("Text Fields", createTextFields())
    result.addTab("Comments", JScrollPane(createCommentsPanel()))
    result.addTab("Groups", createGroupsPanel())

    return result
  }

  fun createTextFields(): JPanel {
    val result = panel {
      row("Text field 1:") {
        textField()
          .columns(10)
          .comment("columns = 10")
      }
      row("Text field 2:") {
        textField()
          .horizontalAlign(HorizontalAlign.FILL)
          .comment("horizontalAlign(HorizontalAlign.FILL)")
      }
      row("Int text field 1:") {
        intTextField()
          .columns(10)
          .comment("columns = 10")
      }
      row("Int text field 2:") {
        intTextField(range = 0..1000)
          .comment("range = 0..1000")
      }
      row("Int text field 2:") {
        intTextField(range = 0..1000, keyboardStep = 100)
          .comment("range = 0..1000, keyboardStep = 100")
      }
    }

    val disposable = Disposer.newDisposable()
    result.registerValidators(disposable)
    Disposer.register(myDisposable, disposable)

    return result
  }

  fun createGroupsPanel(): JPanel {
    return panel {
      lateinit var group1Label: Cell<JLabel>
      val group1 = group(title = "Group1 title") {
        row {
          group1Label = label("Group1 label1")
        }
        row {
          label("Group1 line 2")
        }
      }
      val group2 = group(title = "Group2 title") {
        row {
          label("Group2 content")
        }
      }
      row {
        checkBox("Group1 visibility")
          .applyToComponent {
            isSelected = true
            addChangeListener { group1.visible(this.isSelected) }
          }
        checkBox("Group1 label1 visibility")
          .applyToComponent {
            isSelected = true
            addChangeListener { group1Label.visible(this.isSelected) }
          }
      }
      row {
        checkBox("Group2 visibility")
          .applyToComponent {
            isSelected = true
            addChangeListener { group2.visible(this.isSelected) }
          }
      }
    }
  }

  fun createCommentsPanel(): JPanel {
    return panel {
      for (rowLayout in RowLayout.values()) {
        row("${rowLayout.name}:") {
          checkBox("Long Checkbox1")
            .comment("Checkbox1 comment is aligned with checkbox1")
          checkBox("Checkbox2")
          button("button") { }
        }.layout(rowLayout)
        row("${rowLayout.name} long:") {
          checkBox("Checkbox1")
          checkBox("Long Checkbox2")
            .comment("<html>LABEL_ALIGNED: Checkbox2 comment is aligned with checkbox1 (cell[1]), hard to fix, rare use case<br>" +
                     "OTHERWISE: Checkbox2 comment is aligned with checkbox2")
          button("button") { }
        }.layout(rowLayout)
        row(rowLayout.name) {
          checkBox("Checkbox1")
          checkBox("Long Checkbox2")
          button("button") { }
            .comment("<html>LABEL_ALIGNED: Button comment is aligned with checkbox1 (cell[1]), hard to fix, rare use case<br>" +
                     "OTHERWISE: Button comment is aligned with button")
        }.layout(rowLayout)
        row {
          checkBox("${rowLayout.name} Checkbox:")
            .comment("Checkbox comment is aligned with checkbox")
          checkBox("Checkbox1")
          checkBox("Long Checkbox2")
          button("button") { }
        }.layout(rowLayout)
        row {
          checkBox("${rowLayout.name} Checkbox:")
          checkBox("Checkbox1")
            .comment("Checkbox1 comment is aligned with checkbox1")
          checkBox("Long Checkbox2")
          button("button") { }
        }.layout(rowLayout)
      }
    }
  }
}