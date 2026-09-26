// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JBPasswordFieldPanel : UISandboxPanel {

  override val title: String = "JBPasswordField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      withStateLabel {
        passwordField()
      }
      withStateLabel {
        passwordField().applyToComponent {
          isEditable = false
          text = "Some text"
        }
      }
      withStateLabel {
        passwordField().applyToComponent {
          isEnabled = false
          text = "Some text"
        }
      }
      row(DevkitUiDslBundle.message("sandbox.without.echo.char")) {
        passwordField().applyToComponent {
          echoChar = '\u0000'
        }
      }
      row(DevkitUiDslBundle.message("sandbox.with.empty.text")) {
        passwordField().applyToComponent {
          emptyText.text = "Type some text"
        }
      }
    }
  }
}