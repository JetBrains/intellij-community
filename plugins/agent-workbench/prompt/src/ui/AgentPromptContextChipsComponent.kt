// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.execution.ui.TagButton
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private const val CONTEXT_CHIP_GAP = 6

internal class AgentPromptContextChipsComponent(
  private val onRemove: (ContextEntry) -> Unit,
) {
  private var renderedEntries: List<ContextEntry> = emptyList()

  val component: JPanel = JPanel(
    WrapLayout(
      FlowLayout.LEFT,
      0,
      JBUI.scale(CONTEXT_CHIP_GAP),
    )
  ).apply {
    isOpaque = false
  }

  fun render(entries: List<ContextEntry>) {
    renderedEntries = entries
    component.removeAll()
    entries.forEach { entry ->
      component.add(wrapRowComponent(createContextChip(entry)))
    }
    component.revalidate()
    component.repaint()
  }

  private fun wrapRowComponent(content: JComponent): JComponent {
    return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight(CONTEXT_CHIP_GAP)
      add(content)
    }
  }

  private fun createContextChip(entry: ContextEntry): JComponent {
    val chipIcon = AgentPromptScreenshotChipIcon.resolve(entry.item)
    return ContextChipTagButton(entry.displayText, chipIcon, java.util.function.Consumer { _: Any? ->
      onRemove(entry)
    }).apply {
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

private class ContextChipTagButton(
  @Nls text: String,
  icon: Icon?,
  action: Consumer<in AnActionEvent>,
) : TagButton(text, action) {
  init {
    if (icon != null) {
      myButton.iconTextGap = JBUI.scale(4)
      updateButton(text, icon)
    }
  }
}
