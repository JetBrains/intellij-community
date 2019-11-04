// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel

class GHToolbarLabelAction(private val text: String) : DumbAwareAction(), CustomComponentAction {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
    e.presentation.isVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {}

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    JLabel(text).apply {
      font = JBUI.Fonts.toolbarFont()
      border = JBUI.Borders.empty(0, 6, 0, 5)
    }
}