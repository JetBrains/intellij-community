// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.addText
import com.intellij.internal.ui.sandbox.initWithText
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

internal class JBTextAreaTestPanel : UISandboxPanel {

  override val title: String = "JBTextArea"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        scrollCell(JTextArea())
          .label("JTextArea in JBScrollPane:", LabelPosition.TOP)
          .initWithText()
      }
      row {
        val textArea = JBTextArea().apply {
          rows = 5
          addText()
        }
        cell(JScrollPane(textArea))
          .label("JBTextArea in JScrollPane:", LabelPosition.TOP)
          .align(AlignX.FILL)
      }
      row {
        val textArea = JTextArea().apply {
          rows = 5
          addText()
        }
        cell(JScrollPane(textArea))
          .label("JTextArea in JScrollPane:", LabelPosition.TOP)
          .align(AlignX.FILL)
      }
    }
  }
}