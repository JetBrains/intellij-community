// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import javax.swing.JTextField

/**
 * @author Konstantin Bulenkov
 */
class ConfigureCustomSizeAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    fun validateInt(textField: JTextField): ValidationInfo? {
      val value = textField.text.toIntOrNull()
      return if (value == null || value !in 0..1000) ValidationInfo("Should be integer in range 1..1000") else null
    }

    val centerPanel = panel {
      row("Width:") { intTextField(CustomSizeModel::width, 20).focused().withValidation(::validateInt) }
      row("Height:") { intTextField(CustomSizeModel::height, 20).withValidation(::validateInt) }
    }

    dialog("Default Size", centerPanel, project = e.project).show()
  }

  object CustomSizeModel {
    var width by propComponentProperty(defaultValue = 640)
    var height by propComponentProperty(defaultValue = 300)
  }
}