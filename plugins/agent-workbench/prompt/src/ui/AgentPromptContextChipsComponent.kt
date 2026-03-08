// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.execution.ui.TagButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private const val CONTEXT_CHIP_GAP = 6

internal class AgentPromptContextChipsComponent(
  private val onRemove: (ContextEntry) -> Unit,
) {
  val component: JPanel = JPanel(
    WrapLayout(
      FlowLayout.LEFT,
      JBUI.scale(CONTEXT_CHIP_GAP),
      JBUI.scale(CONTEXT_CHIP_GAP),
    )
  ).apply {
    isOpaque = false
  }

  fun render(entries: List<ContextEntry>) {
    component.removeAll()
    entries.forEach { entry ->
      component.add(createContextChip(entry))
    }
    component.revalidate()
    component.repaint()
  }

  private fun createContextChip(entry: ContextEntry): JComponent {
    return TagButton(entry.displayText) {
      onRemove(entry)
    }.apply {
      isOpaque = false
      isFocusable = false
      // TagButton's inner `styleTag` button is opaque by default; make it non-opaque and
      // transparent so only the rounded tag shape is painted (no rectangular tile behind it).
      components.filterIsInstance<JButton>()
        .filter { it.getClientProperty("styleTag") != null }
        .forEach { button ->
          button.isOpaque = false
          button.background = UIUtil.TRANSPARENT_COLOR
          button.putClientProperty("JButton.backgroundColor", UIUtil.TRANSPARENT_COLOR)
          installPlainTextIdeTooltip(component = button) { entry.tooltipText }
        }
    }
  }
}
