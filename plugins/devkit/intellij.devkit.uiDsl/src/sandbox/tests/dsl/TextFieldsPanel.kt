// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class TextFieldsPanel: UISandboxPanel {

  override val title: String = "Text Fields"

  override fun createContent(disposable: Disposable): JComponent {
    @Suppress("HardCodedStringLiteral")
    val result = panel {
      row(DevkitUiDslBundle.message("sandbox.text.field.1")) {
        textField()
          .columns(10)
          .comment("columns = 10")
      }
      row(DevkitUiDslBundle.message("sandbox.text.field.2")) {
        textField()
          .align(AlignX.FILL)
          .comment("align(AlignX.FILL)")
      }
      row(DevkitUiDslBundle.message("sandbox.int.text.field.1")) {
        intTextField()
          .columns(10)
          .comment("columns = 10")
      }
      row(DevkitUiDslBundle.message("sandbox.int.text.field.2")) {
        intTextField(range = 0..1000)
          .comment("range = 0..1000")
      }
      row(DevkitUiDslBundle.message("sandbox.int.text.field.2")) {
        intTextField(range = 0..1000, keyboardStep = 100)
          .comment("range = 0..1000, keyboardStep = 100")
      }
    }

    result.registerValidators(disposable)

    return result
  }
}