// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.Component
import javax.swing.JComponent

internal class ShowUIDefaultsAddValue(parent: Component, canBeParent: Boolean, private val onAdd: (key: String, value: String) -> Unit) :
  DialogWrapper(parent, canBeParent) {

  private lateinit var name: JBTextField
  private lateinit var value: JBTextField

  init {
    title = IdeBundle.message("dialog.title.add.new.value")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(IdeBundle.message("label.ui.name")) {
        name = textField()
          .focused()
          .columns(COLUMNS_LARGE)
          .align(AlignX.FILL)
          .component
      }
      row(IdeBundle.message("label.ui.value")) {
        value = textField()
          .align(AlignX.FILL)
          .component
      }
    }
  }

  override fun doOKAction() {
    onAdd(name.text, value.text)
    super.doOKAction()
  }
}