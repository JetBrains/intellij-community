// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

internal class ClaudeQuotaHintPanel(
  onEnable: () -> Unit,
  onDismiss: () -> Unit,
) : JPanel(BorderLayout()) {
  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1),
      JBUI.Borders.empty(8),
    )

    val textPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(JLabel(AgentSessionsBundle.message("toolwindow.claude.quota.hint.title")).apply {
        font = font.deriveFont(font.style or Font.BOLD)
      })
      add(Box.createVerticalStrut(JBUI.scale(4)))
      add(JLabel(AgentSessionsBundle.message("toolwindow.claude.quota.hint.body")))
    }

    val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(JButton(AgentSessionsBundle.message("toolwindow.claude.quota.hint.enable")).apply {
        addActionListener { onEnable() }
      })
      add(JButton(AgentSessionsBundle.message("toolwindow.claude.quota.hint.dismiss")).apply {
        addActionListener { onDismiss() }
      })
    }

    add(textPanel, BorderLayout.CENTER)
    add(actionsPanel, BorderLayout.SOUTH)
  }
}
