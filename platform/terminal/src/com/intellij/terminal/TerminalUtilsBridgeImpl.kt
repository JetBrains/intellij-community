// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.actionSystem.DataContext
import com.jediterm.terminal.ui.TerminalPanel
import java.awt.Component

internal class TerminalUtilsBridgeImpl : TerminalUtilsBridge {
  override fun isTerminalComponent(component: Component?): Boolean {
    return component is TerminalPanel
  }

  override fun hasSelectionInTerminal(component: Component?): Boolean {
    return component is TerminalPanel && component.selection != null
  }

  override fun getSelectedTextInTerminal(component: Component?): String? {
    return if (component is TerminalPanel) JBTerminalWidget.getSelectedText(component) else null
  }

  override fun getTextInTerminal(component: Component?): String? {
    return if (component is TerminalPanel) JBTerminalWidget.getText(component) else null
  }

  override fun getSelectedTextInTerminal(context: DataContext): String? {
    return context.getData(JBTerminalWidget.SELECTED_TEXT_DATA_KEY)
  }
}
