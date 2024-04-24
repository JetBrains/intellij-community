// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JTextArea

internal class JBTextAreaPanel : UISandboxPanel {

  override val title: String = "JBTextArea"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        textArea()
          .label("Enabled:", LabelPosition.TOP)
          .align(AlignX.FILL)
          .addText()
          .rows(5)
      }
      row {
        textArea()
          .label("Disabled:", LabelPosition.TOP)
          .align(AlignX.FILL)
          .enabled(false)
          .addText()
          .rows(5)
      }
      row {
        textArea()
          .label("Empty text:", LabelPosition.TOP)
          .align(AlignX.FILL)
          .rows(3)
          .apply {
            component.emptyText.text = "Type some text here"
          }
      }

    }
  }

  fun <T : JTextArea> Cell<T>.addText(): Cell<T> {
    component.text = (1..20).joinToString(separator = "\n") { "Line $it" }
    return this
  }
}