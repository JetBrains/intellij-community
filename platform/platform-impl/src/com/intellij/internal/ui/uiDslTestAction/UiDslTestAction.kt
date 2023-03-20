// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.Border

internal class UiDslTestAction : DumbAwareAction("Show UI DSL Tests") {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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
    val tabbedPane = JBTabbedPane()
    tabbedPane.minimumSize = Dimension(300, 200)
    tabbedPane.preferredSize = Dimension(1000, 800)
    tabbedPane.addTab("Labels", JScrollPane(LabelsPanel().panel))
    tabbedPane.addTab("Text Fields", createTextFields())
    tabbedPane.addTab("Comments", JScrollPane(createCommentsPanel()))
    tabbedPane.addTab("Text MaxLine", createTextMaxLinePanel())
    tabbedPane.addTab("Groups", JScrollPane(GroupsPanel().panel))
    tabbedPane.addTab("Segmented Button", SegmentedButtonPanel(myDisposable).panel)
    tabbedPane.addTab("Visible/Enabled", createVisibleEnabled())
    tabbedPane.addTab("Cells With Sub-Panels", createCellsWithPanels())
    tabbedPane.addTab("Placeholder", PlaceholderPanel(myDisposable).panel)
    tabbedPane.addTab("Resizable Rows", createResizableRows())
    tabbedPane.addTab("Others", OthersPanel().panel)
    tabbedPane.addTab("Deprecated Api", JScrollPane(DeprecatedApiPanel().panel))
    tabbedPane.addTab("CheckBox/RadioButton", CheckBoxRadioButtonPanel().panel)
    tabbedPane.addTab("Validation API", ValidationPanel(myDisposable).panel)
    tabbedPane.addTab("Validation Refactoring API", ValidationRefactoringPanel(myDisposable).panel)
    tabbedPane.addTab("OnChange", OnChangePanel().panel)

    return panel {
      row {
        button("Long texts") {
          LongTextsDialog().show()
        }
      }

      row {
        cell(tabbedPane)
          .align(Align.FILL)
      }.resizableRow()
    }
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
          .align(AlignX.FILL)
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

    result.registerValidators(myDisposable)

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
        }.align(AlignY.TOP)

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
                }.onChanged {
                  when (entity) {
                    is Cell<*> -> entity.visible(it.isSelected)
                    is Row -> entity.visible(it.isSelected)
                    is Panel -> entity.visible(it.isSelected)
                  }
                }
              checkBox("enabled")
                .applyToComponent {
                  isSelected = true
                }.onChanged {
                  when (entity) {
                    is Cell<*> -> entity.enabled(it.isSelected)
                    is Row -> entity.enabled(it.isSelected)
                    is Panel -> entity.enabled(it.isSelected)
                  }
                }
            }
          }
        }.align(AlignX.RIGHT)
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
          .align(AlignX.FILL)
      }
      row("Row 4") {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .align(AlignX.FILL)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
          .align(AlignX.FILL)
      }
    }
  }

  fun createResizableRows(): JPanel {
    return panel {
      for (rowLayout in RowLayout.values()) {
        row(rowLayout.name) {
          textArea()
            .align(Align.FILL)
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
          .onChanged {
            type = it.item ?: CommentComponentType.CHECKBOX
            applyType()
            placeholder.revalidate()
          }
      }
      row {
        cell(placeholder)
      }
    }

    applyType()
    return result
  }

  fun createTextMaxLinePanel(): JPanel {
    val longLine = (1..4).joinToString { "A very long string with a <a>link</a>" }
    val string = "$longLine<br>$longLine"
    return panel {
      row("comment(string):") {
        comment(string)
      }
      row("comment(string, DEFAULT_COMMENT_WIDTH):") {
        comment(string, maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row("comment(string, MAX_LINE_LENGTH_NO_WRAP):") {
        comment(string, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }
      row("text(string):") {
        text(string)
      }
      row("text(string, DEFAULT_COMMENT_WIDTH):") {
        text(string, maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row("text(string, MAX_LINE_LENGTH_NO_WRAP):") {
        text(string, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }
    }
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
