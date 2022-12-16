// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

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

  val panel = panel {
    row {
      checkBox("checkBox")
        .onChanged(::log)
        .bindSelected(::checkBoxValue)
    }
    buttonsGroup {
      row {
        radioButton("true", true)
          .onChanged { component, binding -> log(component, binding, "true") }
        radioButton("false", false)
          .onChanged { component, binding -> log(component, binding, "false") }
      }
    }.bind(::radioButtonValue)
    row {
      textField()
        .onChanged(::log)
        .bindText(::textFieldValue)
    }
    row {
      textArea()
        .applyToComponent { rows = 2 }
        .align(AlignX.FILL)
        .onChanged(::log)
        .bindText(::textAreaValue)
    }
    row {
      comboBox(listOf("Item 1", "Item 2", "Last"))
        .onChanged { component, binding -> log(component, binding, "notEditable") }
        .bindItem(::comboBoxNotEditableValue.toNullableProperty())
      comboBox(listOf("Item 1", "Item 2", "Last"))
        .applyToComponent { isEditable = true }
        .onChanged { component, binding -> log(component, binding, "editable") }
        .bindItemNullable(::comboBoxEditableValue)
    }
    row {
      dropDownLink("Item 1", listOf("Item 1", "Item 2", "Last"))
        .onChanged(::log)
    }

    row {
      scrollCell(log)
        .align(Align.FILL)
    }.resizableRow()
  }

  private fun log(component: JComponent, binding: Boolean, text: String? = null) {
    val textLog = if (text == null) "" else "($text)"
    log.text = log.text + "component = ${component::class.java.name}$textLog, binding = $binding\n"
  }
}