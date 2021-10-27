// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.Border


class UiDslTestAction : DumbAwareAction("Show UI DSL Tests") {

  override fun actionPerformed(e: AnActionEvent) {
    UiDslTestDialog(e.project).show()
  }
}

@Suppress("DialogTitleCapitalization")
private class UiDslTestDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, false) {

  init {
    title = "UI DSL Tests"
    init()
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  override fun createCenterPanel(): JComponent {
    val result = JBTabbedPane()
    result.minimumSize = Dimension(300, 200)
    result.preferredSize = Dimension(800, 600)
    result.addTab("Labels", createLabelsPanel())
    result.addTab("Text Fields", createTextFields())
    result.addTab("Comments", JScrollPane(createCommentsPanel()))
    result.addTab("Groups", JScrollPane(createGroupsPanel()))
    result.addTab("Segmented Button", createSegmentedButton())
    result.addTab("Visible/Enabled", createVisibleEnabled())
    result.addTab("Cells With Sub-Panels", createCellsWithPanels())
    result.addTab("Resizable Rows", createResizableRows())

    return result
  }

  fun createLabelsPanel(): JPanel {
    val result = panel {
      val model = DefaultComboBoxModel<String>()
      var index = 0
      row("Label for row") {
        comboBox(model)
      }
      row {
        val textField = intTextField()
          .text("50")
        button("Add items") {
          val newItems = (1..textField.component.text.toInt()).map { "Item ${index++}" }
          model.addAll(newItems)
        }
        button("Clear") {
          model.removeAllElements()
        }
      }
    }
    val disposable = Disposer.newDisposable()
    result.registerValidators(disposable)
    Disposer.register(myDisposable, disposable)

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
      group(title = "Group at top, no gap before") {
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

      group1 = group(title = "Group1, gaps around") {
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

      row("separator") {}

      collapsibleGroup("CollapsibleGroup") {
        row("Row with label") {}
      }

      row("separator") {}

      group("Group, indent = false, no gaps", indent = false, topGroupGap = false, bottomGroupGap = false) {
        row("Row with label") {}
      }

      row("separator") {}

      groupRowsRange("GroupRowsRange, indent = false, no gaps", indent = false, topGroupGap = false, bottomGroupGap = false) {
        row("Row with label") {}
      }

      row("separator") {}

      collapsibleGroup("CollapsibleGroup, indent = false, no gaps", indent = false, topGroupGap = false, bottomGroupGap = false) {
        row("Row with label") {}
      }

      row("separator") {}

      group("Group at bottom, no gap after") {
        row("Row with label") {}
      }
    }
  }

  fun createSegmentedButton(): JPanel {
    val buttons = listOf("Button 1", "Button 2", "Button Last")
    val propertyGraph = PropertyGraph()
    val property = propertyGraph.graphProperty { "" }
    val rows = mutableMapOf<String, Row>()
    val result = panel {
      row("Segmented Button") {
        segmentedButton(buttons, property, { s -> s })
      }

      rows[buttons[0]] = row(buttons[0]) {
        textField()
      }
      rows[buttons[1]] = row(buttons[1]) {
        checkBox("checkBox")
      }
      rows[buttons[2]] = row(buttons[2]) {
        button("button") {}
      }
    }

    property.afterChange {
      for ((key, row) in rows) {
        row.visible(key == it)
      }
    }

    property.set(buttons[1])

    return result
  }

  fun createVisibleEnabled(): JPanel {
    val entities = mutableMapOf<String, Any>()

    return panel {
      row {
        label("<html>Example test cases:<br>" +
              "1. Hide Group, hide/show sub elements from Group<br>" +
              "  * they shouldn't be visible until Group becomes visible<br>" +
              "  * after Group becomes shown visible state of sub elements correspondent to checkboxes<br>" +
              "2. Similar to 1 test case but with enabled state")
      }

      row {
        panel {
          entities["Row 1"] = row("Row 1") {
            entities["textField1"] = textField()
              .text("textField1")

          }

          entities["Group"] = group("Group") {
            entities["Row 2"] = row("Row 2") {
              entities["textField2"] = textField()
                .text("textField2")
                .comment("Comment with a <a>link</a>")
            }

            entities["Row 3"] = row("Row 3") {
              entities["panel"] = panel {
                row {
                  label("Panel inside row3")
                }

                entities["Row 4"] = row("Row 4") {
                  entities["textField3"] = textField()
                    .text("textField3")
                }
              }
            }
          }
        }.verticalAlign(VerticalAlign.TOP)

        panel {
          row {
            label("Visible/Enabled")
              .bold()
          }
          for ((name, entity) in entities.toSortedMap()) {
            row(name) {
              checkBox("visible")
                .applyToComponent {
                  isSelected = true
                  addItemListener {
                    when (entity) {
                      is Cell<*> -> entity.visible(this.isSelected)
                      is Row -> entity.visible(this.isSelected)
                      is Panel -> entity.visible(this.isSelected)
                    }
                  }
                }
              checkBox("enabled")
                .applyToComponent {
                  isSelected = true
                  addItemListener {
                    when (entity) {
                      is Cell<*> -> entity.enabled(this.isSelected)
                      is Row -> entity.enabled(this.isSelected)
                      is Panel -> entity.enabled(this.isSelected)
                    }
                  }
                }
            }
          }
        }.horizontalAlign(HorizontalAlign.RIGHT)
      }

      group("Control visibility by visibleIf") {
        lateinit var checkBoxText: Cell<JBCheckBox>
        lateinit var checkBoxRow: Cell<JBCheckBox>

        row {
          checkBoxRow = checkBox("Row")
            .applyToComponent { isSelected = true }
          checkBoxText = checkBox("textField")
            .applyToComponent { isSelected = true }
        }

        row("visibleIf test row") {
          textField()
            .text("textField")
            .visibleIf(checkBoxText.selected)
          label("some label")
        }.visibleIf(checkBoxRow.selected)
      }
    }
  }

  fun createCellsWithPanels(): JPanel {
    return panel {
      row("Row") {
        textField()
          .columns(20)
      }
      row("Row 2") {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .columns(20)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
      }
      row("Row 3") {
        textField()
          .horizontalAlign(HorizontalAlign.FILL)
      }
      row("Row 4") {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .horizontalAlign(HorizontalAlign.FILL)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
          .horizontalAlign(HorizontalAlign.FILL)
      }
    }
  }

  fun createResizableRows(): JPanel {
    return panel {
      for (rowLayout in RowLayout.values()) {
        row(rowLayout.name) {
          textArea()
            .horizontalAlign(HorizontalAlign.FILL)
            .verticalAlign(VerticalAlign.FILL)
        }.layout(rowLayout)
          .resizableRow()
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

    val result = panel {
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
        val labelAligned = rowLayout == RowLayout.LABEL_ALIGNED

        group("rowLayout = $rowLayout") {
          row("With Label:") {
            customComponent("Long Component1")
              .comment("Component1 comment is aligned with Component1")
            customComponent("Component2")
            button("button") { }
          }.layout(rowLayout)
          row("With Long Label:") {
            customComponent("Component1")
            customComponent("Long Component2")
              .comment(
                if (labelAligned) "LABEL_ALIGNED: Component2 comment is aligned with Component1 (cell[1]), hard to fix, rare use case"
                else "Component2 comment is aligned with Component2")
            button("button") { }
          }.layout(rowLayout)
          row("With Very Long Label:") {
            customComponent("Component1")
            customComponent("Long Component2")
            button("button") { }
              .comment(if (labelAligned) "LABEL_ALIGNED: Button comment is aligned with Component1 (cell[1]), hard to fix, rare use case"
              else "Button comment is aligned with button")
          }.layout(rowLayout)
          if (labelAligned) {
            row {
              label("LABEL_ALIGNED: in the next row only two first comments are shown")
            }
          }
          row {
            customComponent("Component1 extra width")
              .comment("Component1 comment")
            customComponent("Component2 extra width")
              .comment("Component2 comment<br>second line")
            customComponent("One More Long Component3")
              .comment("Component3 comment")
            button("button") { }
              .comment("Button comment")
          }.layout(rowLayout)
        }
      }
    }
  }

  private fun Row.customComponent(text: String): Cell<JComponent> {
    return when (type) {
      CommentComponentType.CHECKBOX -> checkBox(text)
      CommentComponentType.TEXT_FIELD -> textField().text(text)
      CommentComponentType.TEXT_FIELD_WITH_BROWSE_BUTTON -> textFieldWithBrowseButton().text(text)
    }
  }
}

private enum class CommentComponentType {
  CHECKBOX,
  TEXT_FIELD,
  TEXT_FIELD_WITH_BROWSE_BUTTON
}
