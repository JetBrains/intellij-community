// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
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
      lateinit var group1: Panel
      lateinit var group1Row: Row
      lateinit var group2: RowsRange
      group(title = "Group at top") {
        row {
          checkBox("Group1 visibility")
            .applyToComponent {
              isSelected = true
              addItemListener { group1.visible(this.isSelected) }
            }
        }
        indent {
          row {
            checkBox("Group1 label1 visibility")
              .applyToComponent {
                isSelected = true
                addItemListener { group1Row.visible(this.isSelected) }
              }

          }
        }
        row {
          checkBox("Group2 visibility")
            .applyToComponent {
              isSelected = true
              addItemListener { group2.visible(this.isSelected) }
            }
        }
      }

      row("A very very long label") {
        textField()
      }

      group1 = group(title = "Group1 title") {
        group1Row = row("label1") {
          textField()
        }
        row("label2 long") {
          textField()
        }
      }
      group2 = groupRowsRange(title = "Group RowsRange title") {
        row("label1") {
          textField()
        }
        row("label2 long") {
          textField()
        }
      }
      group {
        row {
          label("Group without title")
        }
      }
      panel {
        row {
          label("Panel")
        }
        row("label1") {
          textField()
        }
        row("label2 long") {
          textField()
        }
      }
    }
  }

  fun createCommentsPanel(): JPanel {
    var type = CommentComponentType.CHECKBOX
    val placeholder = JPanel(BorderLayout())

    fun applyType() {
      val builder = CommentPanelBuilder(type)
      placeholder.removeAll()
      placeholder.add(builder.build(), BorderLayout.CENTER)
    }

    val result: DialogPanel = panel {
      row("Component type") {
        comboBox(CollectionComboBoxModel(CommentComponentType.values().asList()))
          .applyToComponent {
            addItemListener {
              if (it.stateChange == ItemEvent.SELECTED) {
                type = it?.item as? CommentComponentType ?: CommentComponentType.CHECKBOX
                applyType()
                placeholder.revalidate()
              }
            }
          }
      }
      row {
        cell(placeholder)
      }
    }

    applyType()
    return result
  }
}

@Suppress("DialogTitleCapitalization")
private class CommentPanelBuilder(val type: CommentComponentType) {

  fun build(): DialogPanel {
    return panel {
      for (rowLayout in RowLayout.values()) {
        row("${rowLayout.name}:") {
          customComponent("Long Component1")
            .comment("Component1 comment is aligned with Component1")
          customComponent("Component2")
          button("button") { }
        }.layout(rowLayout)
        row("${rowLayout.name} long:") {
          customComponent("Component1")
          customComponent("Long Component2")
            .comment("<html>LABEL_ALIGNED: Component2 comment is aligned with Component1 (cell[1]), hard to fix, rare use case<br>" +
                     "OTHERWISE: Component2 comment is aligned with Component2")
          button("button") { }
        }.layout(rowLayout)
        row(rowLayout.name) {
          customComponent("Component1")
          customComponent("Long Component2")
          button("button") { }
            .comment("<html>LABEL_ALIGNED: Button comment is aligned with Component1 (cell[1]), hard to fix, rare use case<br>" +
                     "OTHERWISE: Button comment is aligned with button")
        }.layout(rowLayout)
        row {
          customComponent("${rowLayout.name} Component:")
            .comment("Component comment is aligned with Component")
          customComponent("Component1")
          customComponent("Long Component2")
          button("button") { }
        }.layout(rowLayout)
        row {
          customComponent("${rowLayout.name} Component:")
          customComponent("Component1")
            .comment("Component1 comment is aligned with Component1")
          customComponent("Long Component2")
          button("button") { }
        }.layout(rowLayout)
      }
    }
  }

  private fun Row.customComponent(text: String): Cell<JComponent> {
    return when (type) {
      CommentComponentType.CHECKBOX -> checkBox(text)
      CommentComponentType.TEXT_FIELD -> textField().applyToComponent { setText(text) }
      CommentComponentType.TEXT_FIELD_WITH_BROWSE_BUTTON -> textFieldWithBrowseButton().applyToComponent { setText(text) }
    }
  }
}

private enum class CommentComponentType {
  CHECKBOX,
  TEXT_FIELD,
  TEXT_FIELD_WITH_BROWSE_BUTTON
}
