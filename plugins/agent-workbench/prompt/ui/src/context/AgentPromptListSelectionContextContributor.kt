// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JList
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.table.TableCellRenderer

private const val MAX_INCLUDED_SELECTION_ENTRIES = 10

private data class UiSelectionSnapshot(
  @JvmField val sourceLabel: String,
  @JvmField val kind: String?,
  @JvmField val entries: List<String>,
)

internal class AgentPromptListSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
    val selection = extractSelection(dataContext) ?: return emptyList()
    if (selection.entries.isEmpty()) {
      return emptyList()
    }

    val included = selection.entries.take(MAX_INCLUDED_SELECTION_ENTRIES)
    val fullContent = renderSelectionContent(selection.sourceLabel, selection.kind, selection.entries)
    val content = renderSelectionContent(selection.sourceLabel, selection.kind, included)
    val payloadFields = LinkedHashMap<String, AgentPromptPayloadValue>()
    payloadFields["entries"] = AgentPromptPayloadValue.Arr(included.map(AgentPromptPayload::str))
    payloadFields["selectedCount"] = AgentPromptPayload.num(selection.entries.size)
    payloadFields["includedCount"] = AgentPromptPayload.num(included.size)
    payloadFields["sourceLabel"] = AgentPromptPayload.str(selection.sourceLabel)
    selection.kind?.let { payloadFields["selectionKind"] = AgentPromptPayload.str(it) }

    val title = selection.kind
                  ?.let { AgentPromptBundle.message("context.list.selection.title", it) }
                ?: AgentPromptBundle.message("context.list.selection.default.title")

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = title,
        body = content,
        payload = AgentPromptPayloadValue.Obj(payloadFields),
        itemId = "list.selection",
        source = "list",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length,
          includedChars = content.length,
          reason = if (selection.entries.size > included.size) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      )
    )
  }

  private fun extractSelection(dataContext: DataContext): UiSelectionSnapshot? {
    val contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    return extractSelectionFromComponent(contextComponent, dataContext)
           ?: extractSelectionFromSelectedItems(dataContext, contextComponent)
  }
}

private fun extractSelectionFromComponent(component: Component?, dataContext: DataContext): UiSelectionSnapshot? {
  if (component == null) {
    return null
  }

  for (candidate in UIUtil.uiTraverser(component)) {
    when (candidate) {
      is JTable -> buildTableSelection(candidate, dataContext)?.let { return it }
      is JList<*> -> buildListSelection(candidate, dataContext)?.let { return it }
    }
  }
  return null
}

private fun buildListSelection(list: JList<*>, dataContext: DataContext): UiSelectionSnapshot? {
  if (list.selectedIndices.isEmpty()) {
    return null
  }

  val entries = LinkedHashSet<String>()
  for (index in list.selectedIndices) {
    renderListEntry(list, index)
      ?.takeIf { it.isNotBlank() }
      ?.let(entries::add)
  }
  if (entries.isEmpty()) {
    return null
  }

  return UiSelectionSnapshot(
    sourceLabel = "List",
    kind = resolveSelectionKind(dataContext, list),
    entries = entries.toList(),
  )
}

private fun buildTableSelection(table: JTable, dataContext: DataContext): UiSelectionSnapshot? {
  if (table.selectedRowCount <= 0) {
    return null
  }

  val entries = LinkedHashSet<String>()
  for (row in table.selectedRows) {
    renderTableRow(table, row)
      ?.takeIf { it.isNotBlank() }
      ?.let(entries::add)
  }
  if (entries.isEmpty()) {
    return null
  }

  return UiSelectionSnapshot(
    sourceLabel = "Table",
    kind = resolveSelectionKind(dataContext, table),
    entries = entries.toList(),
  )
}

private fun extractSelectionFromSelectedItems(dataContext: DataContext, component: Component?): UiSelectionSnapshot? {
  val rawSelection = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext)?.toList().orEmpty()
  val singleSelection = PlatformCoreDataKeys.SELECTED_ITEM.getData(dataContext)?.let(::listOf).orEmpty()
  val selectedItems = rawSelection.ifEmpty { singleSelection }
  if (selectedItems.isEmpty()) {
    return null
  }

  val entries = LinkedHashSet<String>()
  for (value in selectedItems) {
    normalizeValueText(value)
      ?.takeIf { it.isNotBlank() }
      ?.let(entries::add)
  }
  if (entries.isEmpty()) {
    return null
  }

  return UiSelectionSnapshot(
    sourceLabel = "Selection",
    kind = resolveSelectionKind(dataContext, component),
    entries = entries.toList(),
  )
}

private fun renderListEntry(list: JList<*>, index: Int): String? {
  val value = list.model.getElementAt(index)
  val rendererComponent = listCellRendererComponent(list, value, index)
  return extractComponentText(rendererComponent)
         ?: normalizeValueText(value)
}

@Suppress("UNCHECKED_CAST")
private fun listCellRendererComponent(list: JList<*>, value: Any?, index: Int): Component? {
  val renderer = list.cellRenderer as? ListCellRenderer<Any?> ?: return null
  return renderer.getListCellRendererComponent(list as JList<Any?>, value, index, true, false)
}

private fun renderTableRow(table: JTable, row: Int): String? {
  val entries = LinkedHashSet<String>()
  for (column in 0 until table.columnCount) {
    renderTableCell(table, row, column)
      ?.takeIf { it.isNotBlank() }
      ?.let(entries::add)
  }
  return entries.joinToString(separator = " | ").takeIf { it.isNotBlank() }
}

private fun renderTableCell(table: JTable, row: Int, column: Int): String? {
  val value = table.getValueAt(row, column)
  val rendererComponent = tableCellRendererComponent(table, row, column, value)
  return extractComponentText(rendererComponent)
         ?: normalizeValueText(value)
}

private fun tableCellRendererComponent(table: JTable, row: Int, column: Int, value: Any?): Component? {
  val renderer: TableCellRenderer = table.getCellRenderer(row, column)
  return renderer.getTableCellRendererComponent(table, value, true, false, row, column)
}

private fun extractComponentText(component: Component?): String? {
  component ?: return null
  return when (component) {
    is SimpleColoredComponent -> simpleColoredComponentText(component)
    else -> componentText(component)
  }
}

private fun simpleColoredComponentText(component: SimpleColoredComponent): String? {
  val entries = LinkedHashSet<String>()
  val iterator = component.iterator()
  while (iterator.hasNext()) {
    val fragment = iterator.next().trim()
    if (fragment.isNotEmpty()) {
      entries.add(fragment)
    }
  }
  return entries.joinToString(separator = " | ").takeIf { it.isNotBlank() }
         ?: component.accessibleContext?.accessibleName?.trim()?.takeIf { it.isNotEmpty() }
}

private fun componentText(component: Component): String? {
  return component.accessibleContext?.accessibleName?.trim()?.takeIf { it.isNotEmpty() }
         ?: when (component) {
           is javax.swing.JLabel -> component.text?.trim()?.takeIf { it.isNotEmpty() }
           is javax.swing.AbstractButton -> component.text?.trim()?.takeIf { it.isNotEmpty() }
           is javax.swing.text.JTextComponent -> component.text?.trim()?.takeIf { it.isNotEmpty() }
           else -> null
         }
}

private fun normalizeValueText(value: Any?): String? {
  return when (value) {
    null -> null
    is VirtualFile -> value.path
    is FilePath -> value.path
    is java.nio.file.Path -> value.toString()
    else -> value.toString().trim().takeIf { it.isNotEmpty() }
  }
}

private fun resolveSelectionKind(dataContext: DataContext, component: Component?): String? {
  val accessibleName = component?.accessibleContext?.accessibleName?.trim().takeIf { !it.isNullOrEmpty() }
  if (accessibleName != null) {
    return accessibleName
  }
  val toolWindowId = PlatformDataKeys.TOOL_WINDOW.getData(dataContext)?.id?.trim().takeIf { !it.isNullOrEmpty() }
  if (toolWindowId != null) {
    return toolWindowId
  }
  val componentName = component?.name?.trim().takeIf { !it.isNullOrEmpty() }
  if (componentName != null) {
    return componentName
  }
  return null
}

private fun renderSelectionContent(sourceLabel: String, kind: String?, entries: List<String>): String {
  val prefix = if (kind != null) "$sourceLabel: $kind\n" else ""
  return prefix + "Selected:\n" + entries.joinToString(separator = "\n") { "- $it" }
}
