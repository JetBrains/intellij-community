// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec plugins/ij-air/spec/actions/global-prompt-composer.spec.md
// @spec plugins/ij-air/spec/prompt-context/prompt-context-contracts.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItemIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max

private const val CONTEXT_CHIP_GAP = 4
private const val CONTEXT_ATTACHMENT_CARD_MIN_HEIGHT = 26
internal const val CONTEXT_ATTACHMENT_CARD_PROPERTY: String = "agentPromptContextAttachmentCard"
internal const val CONTEXT_ATTACHMENT_OVERFLOW_PROPERTY: String = "agentPromptContextAttachmentOverflow"
internal const val CONTEXT_ATTACHMENT_REMOVE_PROPERTY: String = "agentPromptContextAttachmentRemove"

internal class AgentPromptContextChipsComponent(
  private val maxVisibleRows: Int? = null,
  private val initialAvailableWidth: Int? = null,
  private val onRemoveCompleted: () -> Unit = {},
  private val onRemove: (ContextEntry) -> Unit,
) {
  private var renderedEntries: List<ContextEntry> = emptyList()
  private var renderedWidth: Int = -1

  val component: JPanel = JPanel(ContextChipsWrapLayout(JBUI.scale(CONTEXT_CHIP_GAP), JBUI.scale(CONTEXT_CHIP_GAP))).apply {
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
      component.add(createContextChip(entry))
    }
    if (hiddenCount > 0) {
      component.add(createOverflowChip(hiddenCount))
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
        createContextChip(entry).preferredSize.width
      }
      if (hiddenCount > 0) {
        componentWidths += createOverflowChip(hiddenCount).preferredSize.width
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
      val gap = if (rowWidth == 0) 0 else JBUI.scale(CONTEXT_CHIP_GAP)
      if (rowWidth == 0 || rowWidth + gap + componentWidth <= availableWidth) {
        rowWidth += gap
        rowWidth += componentWidth
      }
      else {
        rows++
        rowWidth = componentWidth
      }
    }
    return rows
  }

  private fun createContextChip(entry: ContextEntry): JComponent {
    return ContextAttachmentCard(entry = entry, icon = resolveContextChipIcon(entry.item)) {
      onRemove(entry)
      onRemoveCompleted()
    }
  }

  private fun createOverflowChip(hiddenCount: Int): JComponent {
    val text = AgentPromptBundle.message("popup.context.overflow", hiddenCount)
    return ContextOverflowAttachmentCard(text)
  }

  private data class ContextChipsSelection(
    @JvmField val visibleEntries: List<ContextEntry>,
    @JvmField val hiddenCount: Int,
  )
}

private class ContextChipsWrapLayout(
  private val hgap: Int,
  private val vgap: Int,
) : LayoutManager {
  override fun addLayoutComponent(name: String?, comp: java.awt.Component?) = Unit

  override fun removeLayoutComponent(comp: java.awt.Component?) = Unit

  override fun preferredLayoutSize(parent: Container): Dimension {
    return layoutSize(parent, usePreferredSize = true)
  }

  override fun minimumLayoutSize(parent: Container): Dimension {
    return layoutSize(parent, usePreferredSize = false)
  }

  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      val insets = parent.insets
      val maxWidth = parent.width - insets.left - insets.right
      var x = insets.left
      var y = insets.top
      var rowHeight = 0

      for (component in parent.components) {
        if (!component.isVisible) continue

        val size = component.preferredSize
        val gap = if (x == insets.left) 0 else hgap
        if (x != insets.left && x + gap + size.width > insets.left + maxWidth) {
          x = insets.left
          y += rowHeight + vgap
          rowHeight = 0
        }

        val effectiveGap = if (x == insets.left) 0 else hgap
        component.setBounds(x + effectiveGap, y, size.width, size.height)
        x += effectiveGap + size.width
        rowHeight = max(rowHeight, size.height)
      }
    }
  }

  private fun layoutSize(parent: Container, usePreferredSize: Boolean): Dimension {
    synchronized(parent.treeLock) {
      val insets = parent.insets
      val targetWidth = parent.width.takeIf { it > 0 }
                        ?: parent.parent?.width?.takeIf { it > 0 }
                        ?: Int.MAX_VALUE
      val maxWidth = targetWidth - insets.left - insets.right
      var width = 0
      var height = 0
      var rowWidth = 0
      var rowHeight = 0

      for (component in parent.components) {
        if (!component.isVisible) continue

        val size = if (usePreferredSize) component.preferredSize else component.minimumSize
        val gap = if (rowWidth == 0) 0 else hgap
        if (rowWidth != 0 && rowWidth + gap + size.width > maxWidth) {
          width = max(width, rowWidth)
          height += if (height == 0) rowHeight else vgap + rowHeight
          rowWidth = 0
          rowHeight = 0
        }

        val effectiveGap = if (rowWidth == 0) 0 else hgap
        rowWidth += effectiveGap + size.width
        rowHeight = max(rowHeight, size.height)
      }

      if (rowWidth > 0) {
        width = max(width, rowWidth)
        height += if (height == 0) rowHeight else vgap + rowHeight
      }

      return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
    }
  }
}

private fun resolveContextChipIcon(item: AgentPromptContextItem): Icon {
  AgentPromptScreenshotChipIcon.resolve(item)?.let { return it }

  return when (item.rendererId) {
    AgentPromptContextRendererIds.FILE -> AllIcons.FileTypes.Any_type
    AgentPromptContextRendererIds.PATHS -> resolvePathsChipIcon(item)
    AgentPromptContextRendererIds.SYMBOL -> AllIcons.Nodes.Method
    AgentPromptContextRendererIds.VCS_COMMITS -> AllIcons.Vcs.CommitNode
    AgentPromptContextRendererIds.TEST_FAILURES -> AllIcons.RunConfigurations.TestState.Red2
    AgentPromptContextRendererIds.SNIPPET -> resolveSnippetChipIcon(item)
    else -> AllIcons.Actions.ListFiles
  }
}

private fun resolveSnippetChipIcon(item: AgentPromptContextItem): Icon {
  return if (item.itemId == AgentPromptContextItemIds.CHANGES_SELECTION || item.source == "changes") {
    AllIcons.Vcs.Changelist
  }
  else {
    AllIcons.Actions.ListFiles
  }
}

private fun resolvePathsChipIcon(item: AgentPromptContextItem): Icon {
  val kinds = extractPathKinds(item)
  return when {
    kinds.isNotEmpty() && kinds.all { it == "dir" } -> AllIcons.Nodes.Folder
    kinds.isNotEmpty() && kinds.all { it == "file" } -> AllIcons.FileTypes.Any_type
    else -> AllIcons.Actions.ListFiles
  }
}

private fun extractPathKinds(item: AgentPromptContextItem): Set<String> {
  val payloadKinds = item.payload.objOrNull()
    ?.array("entries")
    ?.mapNotNull { value ->
      value.objOrNull()
        ?.string("kind")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it == "dir" || it == "file" }
    }
    .orEmpty()
  if (payloadKinds.isNotEmpty()) {
    return payloadKinds.toSet()
  }

  return item.body.lineSequence()
    .mapNotNull { line ->
      when {
        line.trim().startsWith("dir:", ignoreCase = true) -> "dir"
        line.trim().startsWith("file:", ignoreCase = true) -> "file"
        else -> null
      }
    }
    .toSet()
}

private open class ContextAttachmentCardPanel : JPanel(BorderLayout(JBUI.scale(6), 0)) {
  init {
    isOpaque = false
    putClientProperty(CONTEXT_ATTACHMENT_CARD_PROPERTY, true)
    border = JBUI.Borders.empty(5, 8, 5, 6)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val arc = JBUI.scale(10)
      g2.color = UIUtil.getTextFieldBackground()
      g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
      g2.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
      g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
    }
    finally {
      g2.dispose()
    }
    super.paintComponent(g)
  }

  override fun getPreferredSize() = super.getPreferredSize().withAttachmentCardHeight()

  override fun getMinimumSize() = super.getMinimumSize().withAttachmentCardHeight()

  private fun Dimension.withAttachmentCardHeight(): Dimension {
    height = max(height, JBUI.scale(CONTEXT_ATTACHMENT_CARD_MIN_HEIGHT))
    return this
  }
}

private class ContextAttachmentCard(
  entry: ContextEntry,
  icon: Icon,
  onRemove: () -> Unit,
) : ContextAttachmentCardPanel() {
  init {
    getAccessibleContext().accessibleName = entry.accessibleText

    val label = JBLabel(entry.displayText, icon, SwingConstants.LEFT).apply {
      font = JBUI.Fonts.smallFont().asPlain()
      foreground = UIUtil.getLabelForeground()
      iconTextGap = JBUI.scale(6)
      accessibleContext.accessibleName = entry.accessibleText
    }
    installContextChipIdeTooltip(this) { entry }
    installContextChipIdeTooltip(label) { entry }
    add(label, BorderLayout.CENTER)

    add(JButton(AllIcons.Actions.Close).apply {
      putClientProperty(CONTEXT_ATTACHMENT_REMOVE_PROPERTY, true)
      isOpaque = false
      isContentAreaFilled = false
      isBorderPainted = false
      isFocusable = true
      border = JBUI.Borders.empty()
      preferredSize = JBUI.size(16, 16)
      setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.context.remove.tooltip")))
      accessibleContext.accessibleName = AgentPromptBundle.message("popup.context.remove.accessible.name", entry.accessibleText)
      addActionListener { onRemove() }
    }, BorderLayout.EAST)
  }
}

private class ContextOverflowAttachmentCard(text: @Nls String) : ContextAttachmentCardPanel() {
  init {
    putClientProperty(CONTEXT_ATTACHMENT_OVERFLOW_PROPERTY, true)
    getAccessibleContext().accessibleName = text
    add(JBLabel(text).apply {
      font = JBUI.Fonts.smallFont().asPlain()
      foreground = UIUtil.getContextHelpForeground()
      accessibleContext.accessibleName = text
    }, BorderLayout.CENTER)
  }
}
