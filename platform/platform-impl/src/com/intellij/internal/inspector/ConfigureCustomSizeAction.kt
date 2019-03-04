// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * @author Konstantin Bulenkov
 */
class ConfigureCustomSizeAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ConfigureCustomSizeDialog(e.project).show()
  }

  companion object {
    private fun id() = ConfigureCustomSizeAction::class.java
    @JvmStatic
    fun widthId() = "${id()}.width"
    @JvmStatic
    fun heightId() = "${id()}.height"
  }

  class ConfigureCustomSizeDialog(project: Project?): DialogWrapper(project) {
    val width: JTextField
    val height: JTextField

    init {
      title = "Default Size"
      width = JTextField(loadWidth(), 20)
      height = JTextField(loadHeight(), 20)
      init()
    }

    private fun loadWidth() = PropertiesComponent.getInstance().getValue(widthId(), "640")
    private fun loadHeight() = PropertiesComponent.getInstance().getValue(heightId(), "300")

    override fun createCenterPanel(): JComponent? {
      return panel {
        row("Width:") {width()}
        row("Height:") {height()}
      }
    }

    override fun doOKAction() {
      PropertiesComponent.getInstance().setValue(widthId(), width.text)
      PropertiesComponent.getInstance().setValue(heightId(), height.text)
      super.doOKAction()
    }

    override fun doValidate(): ValidationInfo? {
      val errorMessage = "Should be integer in range 1..1000"
      val w = width.text.toIntOrNull()
      val h = height.text.toIntOrNull()
      if (w == null || w < 1 || w > 1000) return ValidationInfo(errorMessage, width)
      if (h == null || h < 1 || h > 1000) return ValidationInfo(errorMessage, height)
      return null
    }
  }
}