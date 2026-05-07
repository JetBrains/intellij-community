// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.actionsButton
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.tabbedPaneHeader
import com.intellij.ui.dsl.builder.text
import javax.swing.JLabel

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.components.title",
      description = "demo.components.description",
      scrollbar = true)
fun demoComponents(): DialogPanel {
  val panel = panel {
    row {
      checkBox(DevkitUiDslBundle.message("demo.components.checkbox"))
    }

    row {
      threeStateCheckBox(DevkitUiDslBundle.message("demo.components.three.state.checkbox"))
    }

    var radioButtonValue = 2
    buttonsGroup {
      row(DevkitUiDslBundle.message("demo.components.radio.button")) {
        radioButton(DevkitUiDslBundle.message("demo.components.value1"), 1)
        radioButton(DevkitUiDslBundle.message("demo.components.value2"), 2)
      }
    }.bind({ radioButtonValue }, { radioButtonValue = it })

    row {
      button(DevkitUiDslBundle.message("demo.components.button")) {}
    }

    row(DevkitUiDslBundle.message("demo.components.action.button")) {
      val action = object : DumbAwareAction(
        DevkitUiDslBundle.message("demo.components.action.text"),
        DevkitUiDslBundle.message("demo.components.action.description"),
        AllIcons.Actions.QuickfixOffBulb,
      ) {
        override fun actionPerformed(e: AnActionEvent) {
        }
      }
      actionButton(action)
    }

    row(DevkitUiDslBundle.message("demo.components.actions.button")) {
      actionsButton(object : DumbAwareAction(DevkitUiDslBundle.message("demo.components.action.one")) {
        override fun actionPerformed(e: AnActionEvent) {
        }
      },
        object : DumbAwareAction(DevkitUiDslBundle.message("demo.components.action.two")) {
          override fun actionPerformed(e: AnActionEvent) {
          }
        })
    }

    row(DevkitUiDslBundle.message("demo.components.segmented.button")) {
      val buttonLast = DevkitUiDslBundle.message("demo.components.button.last")
      segmentedButton(listOf(
        DevkitUiDslBundle.message("demo.components.button1"),
        DevkitUiDslBundle.message("demo.components.button2"),
        buttonLast,
      )) {
        text = it
        if (it == buttonLast) {
          icon = AllIcons.General.Information
        }
      }.apply {
        selectedItem = DevkitUiDslBundle.message("demo.components.button2")
      }
    }

    row(DevkitUiDslBundle.message("demo.components.tabbed.pane.header")) {
      tabbedPaneHeader(listOf(
        DevkitUiDslBundle.message("demo.components.tab1"),
        DevkitUiDslBundle.message("demo.components.tab2"),
        DevkitUiDslBundle.message("demo.components.last.tab"),
      ))
    }

    row(DevkitUiDslBundle.message("demo.components.label")) {
      label(DevkitUiDslBundle.message("demo.components.some.label"))
    }

    row(DevkitUiDslBundle.message("demo.components.text")) {
      text(DevkitUiDslBundle.message("demo.components.text.value"))
    }

    row(DevkitUiDslBundle.message("demo.components.link")) {
      link(DevkitUiDslBundle.message("demo.components.focusable.link")) {}
    }

    row(DevkitUiDslBundle.message("demo.components.browser.link")) {
      browserLink(DevkitUiDslBundle.message("demo.components.browser.link.text"), "https://www.jetbrains.com")
    }

    row(DevkitUiDslBundle.message("demo.components.drop.down.link")) {
      val item1 = DevkitUiDslBundle.message("demo.components.item1")
      dropDownLink(item1, listOf(
        item1,
        DevkitUiDslBundle.message("demo.components.item2"),
        DevkitUiDslBundle.message("demo.components.item3"),
      ))
    }

    row(DevkitUiDslBundle.message("demo.components.icon")) {
      icon(AllIcons.Actions.QuickfixOffBulb)
    }

    row(DevkitUiDslBundle.message("demo.components.context.help")) {
      contextHelp(DevkitUiDslBundle.message("demo.components.context.help.text"),
                  DevkitUiDslBundle.message("demo.components.context.help.title"))
    }

    row(DevkitUiDslBundle.message("demo.components.text.field")) {
      textField()
    }

    row(DevkitUiDslBundle.message("demo.components.password.field")) {
      passwordField().text(DevkitUiDslBundle.message("demo.components.password"))
    }

    row(DevkitUiDslBundle.message("demo.components.text.field.with.browse")) {
      textFieldWithBrowseButton()
    }

    row(DevkitUiDslBundle.message("demo.components.expandable.text.field")) {
      expandableTextField()
    }

    row(DevkitUiDslBundle.message("demo.components.extendable.text.field")) {
      extendableTextField()
    }

    row(DevkitUiDslBundle.message("demo.components.int.text.field")) {
      intTextField(0..100)
    }

    row(DevkitUiDslBundle.message("demo.components.spinner.int")) {
      spinner(0..100)
    }

    row(DevkitUiDslBundle.message("demo.components.spinner.double")) {
      spinner(0.0..100.0, 0.01)
    }

    row(DevkitUiDslBundle.message("demo.components.slider")) {
      slider(0, 10, 1, 5)
        .labelTable(mapOf(
          0 to JLabel("0"),
          5 to JLabel("5"),
          10 to JLabel("10"),
        ))
    }

    row {
      label(DevkitUiDslBundle.message("demo.components.text.area"))
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)
      textArea()
        .rows(5)
        .align(AlignX.FILL)
    }.layout(RowLayout.PARENT_GRID)

    row(DevkitUiDslBundle.message("demo.components.combo.box")) {
      comboBox(listOf(
        DevkitUiDslBundle.message("demo.components.item1"),
        DevkitUiDslBundle.message("demo.components.item2"),
      ))
    }
  }

  return panel
}
