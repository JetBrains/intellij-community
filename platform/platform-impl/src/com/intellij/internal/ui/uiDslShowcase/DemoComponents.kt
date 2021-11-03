// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign

@Suppress("DialogTitleCapitalization")
@Demo(title = "Components",
  description = "There are many different components supported by UI DSL. Here are some of them.",
  scrollbar = true)
fun demoComponents(): DialogPanel {
  return panel {
    row {
      checkBox("checkBox")
    }

    var radioButtonValue = 2
    buttonGroup({ radioButtonValue }, { radioButtonValue = it }) {
      row("radioButton") {
        radioButton("Value 1", 1)
        radioButton("Value 2", 2)
      }
    }

    row {
      button("button") {}
    }

    row("actionButton:") {
      val action = object : DumbAwareAction("Action text", "Action description", AllIcons.Actions.QuickfixOffBulb) {
        override fun actionPerformed(e: AnActionEvent) {
        }
      }
      actionButton(action)
    }

    row("actionsButton:") {
      actionsButton(object : DumbAwareAction("Action one") {
        override fun actionPerformed(e: AnActionEvent) {
        }
      },
        object : DumbAwareAction("Action two") {
          override fun actionPerformed(e: AnActionEvent) {
          }
        })
    }

    row("segmentedButton:") {
      val property = PropertyGraph().graphProperty { "" }
      segmentedButton(listOf("Button 1", "Button 2", "Button Last"), property) { s -> s }
    }

    row("label:") {
      label("Some label")
    }

    row("text:") {
      text("text supports max line width and can contain links, try <a href='https://www.jetbrains.com'>jetbrains.com</a>")
    }

    row("link:") {
      link("Focusable link") {}
    }

    row("browserLink:") {
      browserLink("jetbrains.com", "https://www.jetbrains.com")
    }

    row("dropDownLink:") {
      dropDownLink("Item 1", listOf("Item 1", "Item 2", "Item 3"))
    }

    row("icon:") {
      icon(AllIcons.Actions.QuickfixOffBulb)
    }

    row("contextHelp:") {
      contextHelp("contextHelp description", "contextHelp title")
    }

    row("textField:") {
      textField()
    }

    row("textFieldWithBrowseButton:") {
      textFieldWithBrowseButton()
    }

    row("intTextField(0..100):") {
      intTextField(0..100)
    }

    row("spinner(0..100):") {
      spinner(0..100)
    }

    row("spinner(0.0..100.0, 0.01):") {
      spinner(0.0..100.0, 0.01)
    }

    row {
      label("textArea:")
        .verticalAlign(VerticalAlign.TOP)
        .gap(RightGap.SMALL)
      textArea()
        .rows(5)
        .horizontalAlign(HorizontalAlign.FILL)
    }.layout(RowLayout.PARENT_GRID)

    row("comboBox:") {
      comboBox(arrayOf("Item 1", "Item 2"))
    }
  }
}
