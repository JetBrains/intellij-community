// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRenderers
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.execution.ui.TagButton
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

private const val CHIP_GAP = 6

internal class AgentChatPendingContextPanel(
  private val projectPath: String,
) {
  private val pendingItems = ArrayList<AgentPromptContextItem>()
  private val componentDelegate = lazy(LazyThreadSafetyMode.NONE) { createComponent() }
  private val chipsPanel by lazy(LazyThreadSafetyMode.NONE) {
    JPanel(WrapLayout(FlowLayout.LEFT, 0, JBUI.scale(CHIP_GAP))).apply {
      isOpaque = false
    }
  }
  private val contextPopupMenu by lazy(LazyThreadSafetyMode.NONE) {
    JPopupMenu().apply {
      add(JMenuItem(AgentChatBundle.message("chat.pending.context.clear")).apply {
        addActionListener { clear() }
      })
    }
  }
  private val contextLabel by lazy(LazyThreadSafetyMode.NONE) {
    JLabel(AgentChatBundle.message("chat.pending.context.label")).apply {
      setToolTipText(HtmlChunk.text(AgentChatBundle.message("chat.pending.context.label.tooltip")))
      componentPopupMenu = contextPopupMenu
    }
  }

  val component: JPanel
    get() = componentDelegate.value

  fun addItems(items: List<AgentPromptContextItem>): Boolean {
    val uniqueItems = appendUniqueItems(currentItems = pendingItems, candidateItems = items)
    if (uniqueItems.isEmpty()) {
      return false
    }

    pendingItems += uniqueItems
    if (componentDelegate.isInitialized()) {
      render()
    }
    return true
  }

  fun hasItems(): Boolean = pendingItems.isNotEmpty()

  fun buildPromptSuffix(
    items: List<AgentPromptContextItem> = pendingItems,
    summary: AgentPromptContextEnvelopeSummary = AgentPromptContextEnvelopeSummary(),
  ): String? {
    if (items.isEmpty()) {
      return null
    }
    val contextBlock = AgentPromptContextEnvelopeFormatter.renderContextBlock(
      items = items,
      summary = summary,
      projectPath = projectPath,
    )
    return "\n\n$contextBlock"
  }

  fun measureContextBlockChars(items: List<AgentPromptContextItem> = pendingItems): Int {
    return AgentPromptContextEnvelopeFormatter.measureContextBlockChars(
      items = items,
      summary = AgentPromptContextEnvelopeSummary(
        softCapChars = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS,
        softCapExceeded = false,
        autoTrimApplied = false,
      ),
      projectPath = projectPath,
    )
  }

  fun clear() {
    if (pendingItems.isEmpty()) {
      return
    }
    pendingItems.clear()
    if (componentDelegate.isInitialized()) {
      render()
    }
  }

  internal fun pendingItemsForTests(): List<AgentPromptContextItem> = pendingItems.toList()

  internal fun pendingItemsSnapshot(): List<AgentPromptContextItem> = pendingItems.toList()

  private fun createComponent(): JPanel {
    return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.compound(
        JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
        JBUI.Borders.empty(6, 8),
      )
      componentPopupMenu = contextPopupMenu
      chipsPanel.componentPopupMenu = contextPopupMenu
      add(contextLabel, BorderLayout.WEST)
      add(chipsPanel, BorderLayout.CENTER)
      render(this)
    }
  }

  private fun render(component: JPanel = this.component) {
    chipsPanel.removeAll()
    pendingItems.forEach { item ->
      chipsPanel.add(wrapRowComponent(createContextChip(item)))
    }
    component.isVisible = pendingItems.isNotEmpty()
    component.revalidate()
    component.repaint()
  }

  private fun wrapRowComponent(content: JComponent): JComponent {
    return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight(CHIP_GAP)
      add(content)
    }
  }

  private fun createContextChip(item: AgentPromptContextItem): JComponent {
    val chipRender = AgentPromptContextRenderers.find(item.rendererId)
      ?.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = projectPath))
    val chipText: @NlsSafe String = chipRender?.text ?: buildDefaultDisplayText(item)
    val tooltipText = buildTooltipText(chipRender?.tooltipText, item, projectPath)
    return ContextChipTagButton(chipText, Consumer { _: Any? -> removeItem(item) }).apply {
      isOpaque = false
      isFocusable = false
      components.filterIsInstance<JButton>()
        .filter { it.getClientProperty("styleTag") != null }
        .forEach { button ->
          button.isOpaque = false
          button.background = UIUtil.TRANSPARENT_COLOR
          button.putClientProperty("JButton.backgroundColor", UIUtil.TRANSPARENT_COLOR)
          button.setToolTipText(HtmlChunk.text(tooltipText))
          button.componentPopupMenu = contextPopupMenu
        }
    }
  }

  private fun removeItem(item: AgentPromptContextItem) {
    val index = pendingItems.indexOfFirst { candidate -> pendingContextItemKey(candidate) == pendingContextItemKey(item) }
    if (index < 0) {
      return
    }
    pendingItems.removeAt(index)
    render()
  }
}

private class ContextChipTagButton(
  text: @NlsSafe String,
  action: Consumer<Any?>,
) : TagButton(text, action)

private fun appendUniqueItems(
  currentItems: List<AgentPromptContextItem>,
  candidateItems: List<AgentPromptContextItem>,
): List<AgentPromptContextItem> {
  if (candidateItems.isEmpty()) {
    return emptyList()
  }

  val existingKeys = currentItems.mapTo(HashSet()) { item -> pendingContextItemKey(item) }
  val addedKeys = HashSet<PendingContextItemKey>()
  return candidateItems.filter { item ->
    val key = pendingContextItemKey(item)
    key !in existingKeys && addedKeys.add(key)
  }
}

private data class PendingContextItemKey(
  @JvmField val rendererId: String,
  @JvmField val source: String,
  @JvmField val itemId: String?,
  @JvmField val parentItemId: String?,
  @JvmField val title: String?,
  @JvmField val body: String,
  @JvmField val payload: AgentPromptPayloadValue,
)

private fun pendingContextItemKey(item: AgentPromptContextItem): PendingContextItemKey {
  return PendingContextItemKey(
    rendererId = item.rendererId.trim(),
    source = normalizeContextText(item.source),
    itemId = item.itemId?.let(::normalizeContextText),
    parentItemId = item.parentItemId?.let(::normalizeContextText),
    title = item.title?.let(::normalizeContextText),
    body = normalizeContextText(item.body),
    payload = item.payload,
  )
}

private fun normalizeContextText(value: String): String {
  return value.trim().replace(Regex("\\s+"), " ")
}

private fun buildTooltipText(
  chipTooltipText: String?,
  item: AgentPromptContextItem,
  projectPath: String,
): @NlsSafe String {
  return chipTooltipText ?: AgentPromptContextEnvelopeFormatter.renderContextItem(item = item, projectPath = projectPath)
}

private fun buildDefaultDisplayText(item: AgentPromptContextItem): String {
  val title = item.title?.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.pending.context.default.title")
  val firstLine = item.body.lineSequence().firstOrNull()?.trim().orEmpty()
  if (firstLine.isEmpty()) {
    return title
  }
  val preview = if (firstLine.length <= 60) firstLine else firstLine.take(60) + "..."
  return "$title: $preview"
}
