// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.devkit.uiDsl.showcase

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.layout.selected
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
@Demo(title = "Examples",
      description = "Here are some useful examples, tips and tricks",
      scrollbar = true)
fun demoExamples(): DialogPanel {
  val panel = panel {
    group("Initializing components with extension functions") {
      row {
        label("Bold text")
          .bold()
          .commentRight("'bold()' works for any component")
      }
      row {
        checkBox("Selected CheckBox")
          .selected(true)
          .commentRight("'selected(true)'")
      }
      buttonsGroup {
        row {
          radioButton("RadioButton")
          radioButton("Selected RadioButton")
            .selected(true)
            .commentRight("'selected(true)'")
        }
      }
      row {
        textField()
          .text("Initial text")
          .commentRight("'text(\"Initial text\")'")
      }
    }

    group("CheckBox/RadioButton examples") {
      buttonsGroup("CheckBox/RadioButton Group:") {
        row {
          checkBox("CheckBox 1")
            .orderedContextHelp("Use a colon in the 'buttonsGroup' title",
                                "Left indentation for group elements",
                                "The vertical spacing after the group title is reduced according to the UX spec")
        }
        row {
          checkBox("CheckBox 2")
          browserLink("How it works", "https://www.jetbrains.com/")
            .commentRight("External links must be marked with the external link icon")
        }
      }

      buttonsGroup {
        row("Single line:") {
          radioButton("Option 1")
            .selected(true)
          radioButton("Option 2")
          radioButton("Option 3")
          orderedContextHelp("Horizontal spacing between the label and the CheckBox/RadioButton is increased according to the UX spec",
                             "RadioButtons in a group must be wrapped with 'buttonsGroup {...}'",
                             "javax.swing.ButtonGroup is not needed")
        }
      }

      var radioButtonValue = 0
      buttonsGroup {
        row("Bind value:") {
          radioButton("Value = 1", 1)
          radioButton("Value = 2", 2)
            .commentRight("'buttonsGroup.bind'")
          orderedContextHelp("No RadioButton is selected when the bound value does not match any option; use .selected(true) to define the default selection")
        }
      }.bind({ radioButtonValue }, { radioButtonValue = it })

      row {
        val checkBox = checkBox("Option:")
          .gap(RightGap.SMALL)
          .component
        textField()
          .enabledIf(checkBox.selected)
          .commentRight("'enabledIf'")
        orderedContextHelp("Components that depend on the CheckBox are automatically enabled and disabled using .enabledIf(checkBox.selected)",
                           "Decrease the horizontal space after the CheckBox with 'gap(RightGap.SMALL)'")
      }
    }

    group("TextField") {
      row("Default field:") {
        textField()
          .orderedContextHelp("Labels should end with a colon",
                              "By default, components don’t occupy the full width",
                              "All components in a group/panel are aligned to a common left edge based on the longest label")
      }

      row("") {
        textField()
          .align(AlignX.FILL)
          .orderedContextHelp("Use 'row(\"\")' when no label is needed",
                              "Use 'align(AlignX.FILL)' for full width")
      }
      row("Don't align very long labels with short ones:") {
        textField()
          .columns(COLUMNS_MEDIUM)
          .gap(RightGap.SMALL)
        label("seconds")

        orderedContextHelp("Use&nbsp;'layout(RowLayout.INDEPENDENT)' to align the row independently from other rows",
                           "Use 'gap(RightGap.SMALL)' to reduce the horizontal space before the related label 'seconds'",
                           "Use 'columns(COLUMNS_MEDIUM)' or similar constants to customize the text field width")
      }.layout(RowLayout.INDEPENDENT)

      row("Row 1:") {
        textField()
          .resizableColumn()
          .align(AlignX.FILL)

        button("Test") {
          // Perform an action here
        }
        orderedContextHelp("Use 'resizableColumn()' to allow the column to take up the available width, and 'align(AlignX.FILL)' to expand the text field horizontally",
                           "Use&nbsp;'layout(RowLayout.PARENT_GRID)' to align the components in 'Row 1' and 'Row 2' in a grid-like layout",
                           "Use 'cell()' in 'Row 2' to reserve a grid cell; otherwise, the last component will take up the remaining width")
      }.layout(RowLayout.PARENT_GRID)

      row("Row 2:") {
        textField()
          // .resizableColumn() is not needed because the column is already specified as resizable in 'Row 1'
          .align(AlignX.FILL)
        cell()
      }.layout(RowLayout.PARENT_GRID)
    }

    group("Comments") {
      row {
        checkBox("A very complex option")
          .commentRight("Requires restart")
          .contextHelp("Some description of 'contextHelp'", "A Title")
          .comment("It's important to connect comments to the related cells: " +
                   "they are displayed in the correct location with appropriate styling " +
                   "and used by the accessibility framework")
      }
    }

    group("Buttons") {
      row {
        button("Default Button") {
          // Perform an action here
        }.widthGroup("GroupName")
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }

        button("Button") {
          // Perform an action here
        }.widthGroup("GroupName")

        comment("'widthGroup'")
        orderedContextHelp("All components in the same 'widthGroup' are assigned the maximum width of the group. Cannot be used with 'align(AlignX.FILL)'",
                           "'widthGroup' can be used for any components, e.g. text fields")
      }
    }
  }

  return panel
}

private fun <T : JComponent> Cell<T>.orderedContextHelp(vararg lines: String): Cell<T> {
  return contextHelp(getOrderedList(*lines))
}

private fun Row.orderedContextHelp(vararg lines: String) {
  contextHelp(getOrderedList(*lines))
}

private fun getOrderedList(vararg lines: String): @NlsContexts.Tooltip String {
  assert(lines.isNotEmpty())
  if (lines.size == 1) {
    return lines[0]
  }

  return lines.mapIndexed { i, s -> "${i + 1}.&nbsp;$s" }.joinToString("<br>")
}
