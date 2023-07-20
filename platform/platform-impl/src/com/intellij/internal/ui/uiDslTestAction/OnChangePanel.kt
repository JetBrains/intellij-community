// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class OnChangePanel {

  private val log = JBTextArea().apply {
    isEditable = false
  }

  private var checkBoxValue = true
  private var radioButtonValue = true
  private var textFieldValue = "textField"
  private var textAreaValue = "textArea"
  private var comboBoxNotEditableValue = "Item 2"
  private var comboBoxEditableValue: String? = "Item"
  private var expandableTextFieldValue = "Item"
  private var textFieldWithBrowseButtonValue = "textFieldWithBrowseButton"

  val panel: DialogPanel = panel {
    row {
      checkBox("checkBox")
        .onChangedContext(::log)
        .bindSelected(::checkBoxValue)
    }
    buttonsGroup {
      row {
        radioButton("true", true)
          .onChangedContext { component, context -> log(component, context, "true") }
        radioButton("false", false)
          .onChangedContext { component, context -> log(component, context, "false") }
      }
    }.bind(::radioButtonValue)
    row {
      textField()
        .onChangedContext(::log)
        .bindText(::textFieldValue)
    }
    row {
      textArea()
        .applyToComponent { rows = 2 }
        .align(AlignX.FILL)
        .onChangedContext(::log)
        .bindText(::textAreaValue)
    }
    row {
      comboBox(listOf("Item 1", "Item 2", "Last"))
        .onChangedContext { component, context -> log(component, context, "notEditable") }
        .bindItem(::comboBoxNotEditableValue.toNullableProperty())
      comboBox(listOf("Item 1", "Item 2", "Last"))
        .applyToComponent { isEditable = true }
        .onChangedContext { component, context -> log(component, context, "editable") }
        .bindItem(::comboBoxEditableValue)
    }
    row {
      dropDownLink("Item 1", listOf("Item 1", "Item 2", "Last"))
        .onChangedContext(::log)
    }
    row {
      expandableTextField()
        .align(AlignX.FILL)
        .onChangedContext(::log)
        .bindText(::expandableTextFieldValue)
    }
    row {
      textFieldWithBrowseButton()
        .align(AlignX.FILL)
        .onChangedContext(::log)
        .bindText(::textFieldWithBrowseButtonValue)
    }

    row {
      scrollCell(log)
        .align(Align.FILL)
    }.resizableRow()
  }

  private fun log(component: JComponent, context: ChangeContext, text: String? = null) {
    val textLog = if (text == null) "" else "($text)"
    log.text += "component = ${component::class.java.name}$textLog, binding = ${context.binding}, event: ${context.event}\n"
  }
}