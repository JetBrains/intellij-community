// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.util.Alarm
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.binding.title",
      description = "demo.binding.description",
      scrollbar = true)
fun demoBinding(parentDisposable: Disposable): DialogPanel {
  lateinit var lbIsModified: JLabel
  lateinit var lbModel: JLabel
  lateinit var panel: DialogPanel
  val alarm = Alarm(parentDisposable)
  val model = Model()

  fun initValidation() {
    alarm.addRequest(Runnable {
      val modified = panel.isModified()
      lbIsModified.text = DevkitUiDslBundle.message("demo.binding.is.modified", modified)
      lbIsModified.bold(modified)
      lbModel.text = "<html>$model"

      initValidation()
    }, 1000)
  }

  panel = panel {
    row {
      checkBox(DevkitUiDslBundle.message("demo.binding.checkbox"))
        .bindSelected(model::checkbox)
    }
    row(DevkitUiDslBundle.message("demo.binding.text.field")) {
      textField()
        .bindText(model::textField)
    }
    row(DevkitUiDslBundle.message("demo.binding.int.text.field")) {
      intTextField()
        .bindIntText(model::intTextField)
    }
    row(DevkitUiDslBundle.message("demo.binding.combo.box")) {
      comboBox(Color.entries)
        .bindItem(model::comboBoxColor.toNullableProperty())
    }
    row(DevkitUiDslBundle.message("demo.binding.slider")) {
      slider(0, 100, 10, 50)
        .bindValue(model::slider)
    }
    row(DevkitUiDslBundle.message("demo.binding.spinner")) {
      spinner(0..100)
        .bindIntValue(model::spinner)
    }
    buttonsGroup(DevkitUiDslBundle.message("demo.binding.radio.button")) {
      for (value in Color.entries) {
        row {
          radioButton(value.name, value)
        }
      }
    }.bind(model::radioButtonColor)

    group(DevkitUiDslBundle.message("demo.binding.group.control")) {
      row {
        button(DevkitUiDslBundle.message("demo.binding.reset")) {
          panel.reset()
        }
        button(DevkitUiDslBundle.message("demo.binding.apply")) {
          panel.apply()
        }
        lbIsModified = label("").component
      }
      row {
        lbModel = label("").component
      }
    }
  }

  SwingUtilities.invokeLater {
    initValidation()
  }

  return panel
}

private fun JComponent.bold(isBold: Boolean) {
  font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
}

internal data class Model(
  var checkbox: Boolean = false,
  var textField: String = "",
  var intTextField: Int = 0,
  var comboBoxColor: Color = Color.GREY,
  var slider: Int = 0,
  var spinner: Int = 0,
  var radioButtonColor: Color = Color.GREY,
)

internal enum class Color {
  WHITE,
  GREY,
  BLACK
}
