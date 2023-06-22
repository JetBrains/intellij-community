// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.messages.MessageDialog
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

@ApiStatus.Internal
internal class JBOptionButtonPanel {

  val panel: DialogPanel = panel {
    optionsRow(true, true)
    optionsRow(true, false)
    optionsRow(false, true)
    optionsRow(false, false)
  }

  private fun Panel.optionsRow(enabled: Boolean, singleAction: Boolean) {
    val label = "${if (enabled) "Enabled" else "Disabled"}, ${if (singleAction) "single action" else "multiple actions"}"
    val options = if (singleAction) emptyArray<Action>() else arrayOf(action("Action 1"), action("Action 2"), action("Action 3"))
    row(label) {
      cell(JBOptionButton(action("Some Long Action").apply { isEnabled = enabled }, options))
      cell(JBOptionButton(action("Some Long Action").apply { isEnabled = enabled }, options))
        .applyToComponent {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
    }
  }

  private fun action(text: String): Action {
    return object : AbstractAction(text) {
      override fun actionPerformed(e: ActionEvent?) {
        MessageDialog(null, "Invoked $text", text, emptyArray<String>(), -1, null, false).show()
      }
    }
  }
}
