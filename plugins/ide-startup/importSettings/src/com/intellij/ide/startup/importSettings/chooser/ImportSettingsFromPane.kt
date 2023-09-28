// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale.scaleFontSize
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants


class ImportSettingsFromPane(group: ActionGroup) : JPanel(VerticalLayout(JBUI.scale(32), SwingConstants.CENTER)) {
  private val actionToolbar = ActionManager.getInstance().createActionToolbar("ImportSettingsFrom", group, false).apply {
    if (this is ActionToolbarImpl) {

      isOpaque = false
      setMinimumButtonSize {
        JBUI.size(280, 40)
      }
      setActionButtonBorder(JBUI.scale(9), JBUI.CurrentTheme.RunWidget.toolbarBorderHeight())
    }
  }

  init {
    add(JPanel(VerticalLayout(JBUI.scale(36)).apply {
      add(JLabel("Import Settings").apply {
        font = Font(font.getFontName(), Font.PLAIN, scaleFontSize(24f))
      })
    }))

    add(actionToolbar.component)

    preferredSize = JBDimension(640, 467)
    background = Color.CYAN
  }



}