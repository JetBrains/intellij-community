// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
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
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.examples.title",
      description = "demo.examples.description",
      scrollbar = true)
fun demoExamples(): DialogPanel {
  val panel = panel {
    group(DevkitUiDslBundle.message("demo.examples.group.initializing")) {
      row {
        label(DevkitUiDslBundle.message("demo.examples.bold.text"))
          .bold()
          .commentRight(DevkitUiDslBundle.message("demo.examples.bold.comment"))
      }
      row {
        checkBox(DevkitUiDslBundle.message("demo.examples.selected.checkbox"))
          .selected(true)
          .commentRight(DevkitUiDslBundle.message("demo.examples.selected.true.comment"))
      }
      buttonsGroup {
        row {
          radioButton(DevkitUiDslBundle.message("demo.examples.radio.button"))
          radioButton(DevkitUiDslBundle.message("demo.examples.selected.radio.button"))
            .selected(true)
            .commentRight(DevkitUiDslBundle.message("demo.examples.selected.true.comment"))
        }
      }
      row {
        textField()
          .text(DevkitUiDslBundle.message("demo.examples.initial.text"))
          .commentRight(DevkitUiDslBundle.message("demo.examples.text.initial.comment"))
      }
    }

    group(DevkitUiDslBundle.message("demo.examples.group.checkbox.radio")) {
      buttonsGroup(DevkitUiDslBundle.message("demo.examples.checkbox.radio.group")) {
        row {
          checkBox(DevkitUiDslBundle.message("demo.examples.checkbox1"))
            .orderedContextHelp(DevkitUiDslBundle.message("demo.examples.checkbox1.help.1"),
                                DevkitUiDslBundle.message("demo.examples.checkbox1.help.2"),
                                DevkitUiDslBundle.message("demo.examples.checkbox1.help.3"))
        }
        row {
          checkBox(DevkitUiDslBundle.message("demo.examples.checkbox2"))
          browserLink(DevkitUiDslBundle.message("demo.examples.how.it.works"), "https://www.jetbrains.com/")
            .commentRight(DevkitUiDslBundle.message("demo.examples.external.link.comment"))
        }
      }

      buttonsGroup {
        row(DevkitUiDslBundle.message("demo.examples.single.line")) {
          radioButton(DevkitUiDslBundle.message("demo.examples.option1"))
            .selected(true)
          radioButton(DevkitUiDslBundle.message("demo.examples.option2"))
          radioButton(DevkitUiDslBundle.message("demo.examples.option3"))
          orderedContextHelp(DevkitUiDslBundle.message("demo.examples.single.line.help.1"),
                             DevkitUiDslBundle.message("demo.examples.single.line.help.2"),
                             DevkitUiDslBundle.message("demo.examples.single.line.help.3"))
        }
      }

      var radioButtonValue = 0
      buttonsGroup {
        row(DevkitUiDslBundle.message("demo.examples.bind.value")) {
          radioButton(DevkitUiDslBundle.message("demo.examples.value.1"), 1)
          radioButton(DevkitUiDslBundle.message("demo.examples.value.2"), 2)
            .commentRight(DevkitUiDslBundle.message("demo.examples.buttons.group.bind.comment"))
          orderedContextHelp(DevkitUiDslBundle.message("demo.examples.bind.value.help"))
        }
      }.bind({ radioButtonValue }, { radioButtonValue = it })

      row {
        val checkBox = checkBox(DevkitUiDslBundle.message("demo.examples.option"))
          .gap(RightGap.SMALL)
          .component
        textField()
          .enabledIf(checkBox.selected)
          .commentRight(DevkitUiDslBundle.message("demo.examples.enabled.if.comment"))
        orderedContextHelp(DevkitUiDslBundle.message("demo.examples.option.help.1"),
                           DevkitUiDslBundle.message("demo.examples.option.help.2"))
      }
    }

    group(DevkitUiDslBundle.message("demo.examples.group.text.field")) {
      row(DevkitUiDslBundle.message("demo.examples.default.field")) {
        textField()
          .orderedContextHelp(DevkitUiDslBundle.message("demo.examples.default.field.help.1"),
                              DevkitUiDslBundle.message("demo.examples.default.field.help.2"),
                              DevkitUiDslBundle.message("demo.examples.default.field.help.3"))
      }

      row("") {
        textField()
          .align(AlignX.FILL)
          .orderedContextHelp(DevkitUiDslBundle.message("demo.examples.empty.row.help.1"),
                              DevkitUiDslBundle.message("demo.examples.empty.row.help.2"))
      }
      row(DevkitUiDslBundle.message("demo.examples.long.label")) {
        textField()
          .columns(COLUMNS_MEDIUM)
          .gap(RightGap.SMALL)
        label(DevkitUiDslBundle.message("demo.examples.seconds"))

        orderedContextHelp(DevkitUiDslBundle.message("demo.examples.long.label.help.1"),
                           DevkitUiDslBundle.message("demo.examples.long.label.help.2"),
                           DevkitUiDslBundle.message("demo.examples.long.label.help.3"))
      }.layout(RowLayout.INDEPENDENT)

      row(DevkitUiDslBundle.message("demo.examples.row1")) {
        textField()
          .resizableColumn()
          .align(AlignX.FILL)

        button(DevkitUiDslBundle.message("demo.examples.test")) {
          // Perform an action here
        }
        orderedContextHelp(DevkitUiDslBundle.message("demo.examples.row1.help.1"),
                           DevkitUiDslBundle.message("demo.examples.row1.help.2"),
                           DevkitUiDslBundle.message("demo.examples.row1.help.3"))
      }.layout(RowLayout.PARENT_GRID)

      row(DevkitUiDslBundle.message("demo.examples.row2")) {
        textField()
          // .resizableColumn() is not needed because the column is already specified as resizable in 'Row 1'
          .align(AlignX.FILL)
        cell()
      }.layout(RowLayout.PARENT_GRID)
    }

    group(DevkitUiDslBundle.message("demo.examples.group.comments")) {
      row {
        checkBox(DevkitUiDslBundle.message("demo.examples.complex.option"))
          .commentRight(DevkitUiDslBundle.message("demo.examples.requires.restart"))
          .contextHelp(DevkitUiDslBundle.message("demo.examples.context.help.description"),
                       DevkitUiDslBundle.message("demo.examples.context.help.title"))
          .comment(DevkitUiDslBundle.message("demo.examples.complex.option.comment"))
      }
    }

    group(DevkitUiDslBundle.message("demo.examples.group.buttons")) {
      row {
        button(DevkitUiDslBundle.message("demo.examples.default.button")) {
          // Perform an action here
        }.widthGroup("GroupName")
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }

        button(DevkitUiDslBundle.message("demo.examples.button")) {
          // Perform an action here
        }.widthGroup("GroupName")

        comment(DevkitUiDslBundle.message("demo.examples.width.group.comment"))
        orderedContextHelp(DevkitUiDslBundle.message("demo.examples.buttons.help.1"),
                           DevkitUiDslBundle.message("demo.examples.buttons.help.2"))
      }
    }
  }

  return panel
}

private fun <T : JComponent> Cell<T>.orderedContextHelp(@Nls vararg lines: String): Cell<T> {
  return contextHelp(getOrderedList(*lines))
}

private fun Row.orderedContextHelp(@Nls vararg lines: String) {
  contextHelp(getOrderedList(*lines))
}

private fun getOrderedList(@Nls vararg lines: String): @NlsContexts.Tooltip String {
  assert(lines.isNotEmpty())
  if (lines.size == 1) {
    return lines[0]
  }

  @NonNls val separator = "<br>"
  @Suppress("HardCodedStringLiteral")
  return lines.mapIndexed { i, s -> "${i + 1}.&nbsp;$s" }.joinToString(separator)
}
