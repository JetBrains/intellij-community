// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.applyStateText
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.messages.MessageDialog
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class JBOptionButtonPanel : UISandboxPanel {

  override val title: String = "JBOptionButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.single.action")) {
        optionsRow(true, true)
        optionsRow(false, true)
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.multiple.actions")) {
        optionsRow(true, false)
        optionsRow(false, false)
      }
    }
  }

  private fun Panel.optionsRow(enabled: Boolean, singleAction: Boolean) {
    row {
      val options = if (singleAction) emptyArray<Action>() else arrayOf(action("Action 1"), action("Action 2"), action("Action 3"))
      cell(JBOptionButton(action("").apply { isEnabled = enabled }, options))
        .applyStateText()
      cell(JBOptionButton(action("").apply { isEnabled = enabled }, options))
        .applyToComponent {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }.applyStateText()
    }
  }

  private fun action(text: @NlsSafe String): Action {
    return object : AbstractAction(text) {
      override fun actionPerformed(e: ActionEvent?) {
        MessageDialog(null, DevkitUiDslBundle.message("sandbox.dialog.message.invoked", text), text, emptyArray<String>(), -1, null, false).show()
      }
    }
  }
}