// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.execution.ui.TagButton
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private const val CONTEXT_CHIP_GAP = 4

internal class AgentPromptContextChipsComponent(
  private val maxVisibleRows: Int? = null,
  private val initialAvailableWidth: Int? = null,
  private val onRemove: (ContextEntry) -> Unit,
) {
  private var renderedEntries: List<ContextEntry> = emptyList()
  private var renderedWidth: Int = -1

  val component: JPanel = JPanel(chipLayout()).apply {
    isOpaque = false
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (maxVisibleRows != null && width != renderedWidth) {
          rebuildChips()
        }
      }
    })
  }

  fun render(entries: List<ContextEntry>) {
    renderedEntries = entries
    rebuildChips()
  }

  private fun rebuildChips() {
    renderedWidth = component.width
    component.removeAll()

    val visibleEntries: List<ContextEntry>
    val hiddenCount: Int
    val availableWidth = component.width.takeIf { it > 0 } ?: initialAvailableWidth?.takeIf { it > 0 }
    if (maxVisibleRows == null || availableWidth == null) {
      visibleEntries = renderedEntries
      hiddenCount = 0
    }
    else {
      val selection = selectVisibleEntries(availableWidth)
      visibleEntries = selection.visibleEntries
      hiddenCount = selection.hiddenCount
    }

    visibleEntries.forEach { entry ->
      component.add(wrapRowComponent(createContextChip(entry)))
    }
    if (hiddenCount > 0) {
      component.add(wrapRowComponent(createOverflowChip(hiddenCount)))
    }
    component.revalidate()
    component.repaint()
  }

  private fun selectVisibleEntries(availableWidth: Int): ContextChipsSelection {
    if (renderedEntries.isEmpty()) {
      return ContextChipsSelection(visibleEntries = emptyList(), hiddenCount = 0)
    }

    for (visibleCount in renderedEntries.size downTo 0) {
      val hiddenCount = renderedEntries.size - visibleCount
      val componentWidths = ArrayList<Int>(visibleCount + if (hiddenCount > 0) 1 else 0)
      renderedEntries.take(visibleCount).mapTo(componentWidths) { entry ->
        wrapRowComponent(createContextChip(entry)).preferredSize.width
      }
      if (hiddenCount > 0) {
        componentWidths += wrapRowComponent(createOverflowChip(hiddenCount)).preferredSize.width
      }
      if (rowCount(componentWidths, availableWidth) <= checkNotNull(maxVisibleRows)) {
        return ContextChipsSelection(
          visibleEntries = renderedEntries.take(visibleCount),
          hiddenCount = hiddenCount,
        )
      }
    }

    return ContextChipsSelection(visibleEntries = emptyList(), hiddenCount = renderedEntries.size)
  }

  private fun rowCount(componentWidths: List<Int>, availableWidth: Int): Int {
    if (componentWidths.isEmpty()) {
      return 0
    }

    var rows = 1
    var rowWidth = 0
    for (componentWidth in componentWidths) {
      if (rowWidth == 0 || rowWidth + componentWidth <= availableWidth) {
        rowWidth += componentWidth
      }
      else {
        rows++
        rowWidth = componentWidth
      }
    }
    return rows
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
    return ContextChipTagButton(entry.displayText, chipIcon, Consumer { _: Any? ->
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
          installContextChipIdeTooltip(component = button) { entry }
      }
    }
  }

  private fun createOverflowChip(hiddenCount: Int): JComponent {
    val text = AgentPromptBundle.message("popup.context.overflow", hiddenCount)
    return JButton(text).apply {
      putClientProperty("styleTag", true)
      font = JBUI.Fonts.smallFont().asPlain()
      isFocusable = false
      isOpaque = false
      background = UIUtil.TRANSPARENT_COLOR
      putClientProperty("JButton.backgroundColor", UIUtil.TRANSPARENT_COLOR)
      accessibleContext.accessibleName = text
    }
  }

  private data class ContextChipsSelection(
    @JvmField val visibleEntries: List<ContextEntry>,
    @JvmField val hiddenCount: Int,
  )

  private companion object {
    fun chipLayout(): WrapLayout = WrapLayout(
      FlowLayout.LEFT,
      0,
      JBUI.scale(CONTEXT_CHIP_GAP),
    )
  }
}

private class ContextChipTagButton(
  @Nls text: String,
  icon: Icon?,
  action: Consumer<in AnActionEvent>,
) : TagButton(text, action) {
  init {
    myButton.font = JBUI.Fonts.smallFont().asPlain()
    if (icon != null) {
      myButton.iconTextGap = JBUI.scale(4)
      updateButton(text, icon)
    }
    else {
      layoutButtons()
    }
  }
}
