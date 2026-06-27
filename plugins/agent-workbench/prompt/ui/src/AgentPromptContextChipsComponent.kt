// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-composer.spec.md
// @spec community/plugins/agent-workbench/spec/prompt-context/prompt-context-contracts.spec.md

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
import com.intellij.util.ui.WrapLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
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
      border = JBUI.Borders.empty(0, 0, CONTEXT_CHIP_GAP, CONTEXT_CHIP_GAP)
      add(content)
    }
  }

  private fun createContextChip(entry: ContextEntry): JComponent {
    return ContextAttachmentCard(entry = entry, icon = resolveContextChipIcon(entry.item)) { onRemove(entry) }
  }

  private fun createOverflowChip(hiddenCount: Int): JComponent {
    val text = AgentPromptBundle.message("popup.context.overflow", hiddenCount)
    return ContextOverflowAttachmentCard(text)
  }

  private data class ContextChipsSelection(
    @JvmField val visibleEntries: List<ContextEntry>,
    @JvmField val hiddenCount: Int,
  )

  private companion object {
    fun chipLayout(): WrapLayout = WrapLayout(
      FlowLayout.LEFT,
      0,
      0,
    )
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
