// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

// @spec community/plugins/agent-workbench/spec/thread-view/agent-thread-view-structure.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.DateFormatUtil
import javax.swing.Icon

const val AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID: String = "agent.workbench.thread.view.thread.outline"

internal object AgentThreadViewThreadOutlineDataKeys {
  @JvmField
  val SELECTED_TARGET: DataKey<AgentThreadViewThreadOutlineTarget> = DataKey.create("agent.workbench.thread.view.thread.outline.selectedTarget")
}

internal data class AgentThreadViewThreadOutlineTarget(
  @JvmField val file: AgentThreadViewVirtualFile,
  @JvmField val source: AgentSessionSource,
  @JvmField val item: AgentSessionOutlineItem,
)

internal data class AgentThreadViewThreadOutlineModel(
  @JvmField val rootIds: List<AgentThreadViewThreadOutlineId>,
  @JvmField val entriesById: Map<AgentThreadViewThreadOutlineId, AgentThreadViewThreadOutlineEntry>,
  @JvmField val autoExpandIds: List<AgentThreadViewThreadOutlineId> = emptyList(),
) {
  companion object {
    val EMPTY: AgentThreadViewThreadOutlineModel = status(
      title = AgentThreadViewBundle.message("thread.view.thread.outline.no.selection"),
      status = null,
      icon = null,
    )

    fun status(
      title: @NlsSafe String,
      status: @NlsSafe String?,
      icon: Icon?,
    ): AgentThreadViewThreadOutlineModel {
      val id = AgentThreadViewThreadOutlineId.Status
      return AgentThreadViewThreadOutlineModel(
        rootIds = listOf(id),
        entriesById = mapOf(
          id to AgentThreadViewThreadOutlineEntry(
            id = id,
            parentId = null,
            node = AgentThreadViewThreadOutlineNode.Status(
              title = title,
              status = status,
              icon = icon,
            ),
          )
        ),
      )
    }
  }
}

internal data class AgentThreadViewThreadOutlineEntry(
  @JvmField val id: AgentThreadViewThreadOutlineId,
  @JvmField val parentId: AgentThreadViewThreadOutlineId?,
  @JvmField val node: AgentThreadViewThreadOutlineNode,
  @JvmField val childIds: List<AgentThreadViewThreadOutlineId> = emptyList(),
)

internal data class AgentThreadViewThreadOutlineModelDiff(
  @JvmField val rootChanged: Boolean,
  @JvmField val structureChangedIds: Set<AgentThreadViewThreadOutlineId>,
  @JvmField val contentChangedIds: Set<AgentThreadViewThreadOutlineId>,
)

internal sealed interface AgentThreadViewThreadOutlineId {
  data object Status : AgentThreadViewThreadOutlineId
  data class Item(@JvmField val ordinalPath: String) : AgentThreadViewThreadOutlineId
}

internal sealed interface AgentThreadViewThreadOutlineNode {
  val title: @NlsSafe String
  val timestamp: @NlsSafe String?
  val location: @NlsSafe String?
  val tooltip: @NlsSafe String?
  val icon: Icon?
  val target: AgentThreadViewThreadOutlineTarget?

  data class Status(
    override val title: @NlsSafe String,
    @JvmField val status: @NlsSafe String?,
    override val icon: Icon?,
  ) : AgentThreadViewThreadOutlineNode {
    override val timestamp: @NlsSafe String? get() = null
    override val location: @NlsSafe String? get() = status
    override val tooltip: @NlsSafe String? get() = status
    override val target: AgentThreadViewThreadOutlineTarget? get() = null
  }

  data class Item(
    override val title: @NlsSafe String,
    override val timestamp: @NlsSafe String?,
    override val location: @NlsSafe String?,
    override val tooltip: @NlsSafe String?,
    override val icon: Icon?,
    override val target: AgentThreadViewThreadOutlineTarget,
  ) : AgentThreadViewThreadOutlineNode
}

internal fun buildAgentThreadViewThreadOutlineModel(
  file: AgentThreadViewVirtualFile,
  source: AgentSessionSource,
  outline: AgentSessionThreadOutline,
): AgentThreadViewThreadOutlineModel {
  if (outline.items.isEmpty()) {
    return AgentThreadViewThreadOutlineModel.status(
      title = outline.title,
      status = AgentThreadViewBundle.message("thread.view.thread.outline.empty"),
      icon = providerIcon(provider = outline.provider),
    )
  }

  val entriesById = LinkedHashMap<AgentThreadViewThreadOutlineId, AgentThreadViewThreadOutlineEntry>()
  val rootIds = outline.items.mapIndexed { index, item ->
    addAgentThreadViewThreadOutlineItem(
      entriesById = entriesById,
      file = file,
      source = source,
      item = item,
      parentId = null,
      ordinalPath = index.toString(),
    )
  }
  return AgentThreadViewThreadOutlineModel(
    rootIds = rootIds,
    entriesById = entriesById,
    autoExpandIds = if (rootIds.size == 1) rootIds else emptyList(),
  )
}

private fun addAgentThreadViewThreadOutlineItem(
  entriesById: MutableMap<AgentThreadViewThreadOutlineId, AgentThreadViewThreadOutlineEntry>,
  file: AgentThreadViewVirtualFile,
  source: AgentSessionSource,
  item: AgentSessionOutlineItem,
  parentId: AgentThreadViewThreadOutlineId?,
  ordinalPath: String,
): AgentThreadViewThreadOutlineId {
  val id = AgentThreadViewThreadOutlineId.Item(ordinalPath)
  val childIds = item.children.mapIndexed { index, child ->
    addAgentThreadViewThreadOutlineItem(
      entriesById = entriesById,
      file = file,
      source = source,
      item = child,
      parentId = id,
      ordinalPath = "$ordinalPath/$index",
    )
  }
  entriesById[id] = AgentThreadViewThreadOutlineEntry(
    id = id,
    parentId = parentId,
    node = item.toAgentThreadViewThreadOutlineNode(file = file, source = source),
    childIds = childIds,
  )
  return id
}

private fun AgentSessionOutlineItem.toAgentThreadViewThreadOutlineNode(
  file: AgentThreadViewVirtualFile,
  source: AgentSessionSource,
): AgentThreadViewThreadOutlineNode.Item {
  val timestamp = timestampMs?.let(::formatThreadOutlineTimestamp)
  val title = threadOutlineTitle()
  val location = compactThreadOutlineLocation(preview).takeIf { location -> location != title.threadOutlineTitleContent() }
  return AgentThreadViewThreadOutlineNode.Item(
    title = title,
    timestamp = timestamp,
    location = location,
    tooltip = buildThreadOutlineTooltip(
      preview = preview,
      timestamp = timestampMs?.let(::formatThreadOutlineTimestampTooltip),
    ),
    icon = threadOutlineIcon(),
    target = AgentThreadViewThreadOutlineTarget(file = file, source = source, item = this),
  )
}

private fun AgentSessionOutlineItem.threadOutlineTitle(): String {
  val primaryText = title.takeIf { it.isNotBlank() } ?: compactThreadOutlineLocation(preview)
  val rolePrefix = kind.threadOutlineRolePrefix() ?: return primaryText ?: kind.localizedThreadOutlineTitle()
  return primaryText?.let { text -> "$rolePrefix: $text" } ?: kind.localizedThreadOutlineTitle()
}

private fun String.threadOutlineTitleContent(): String {
  val separatorIndex = indexOf(": ")
  return if (separatorIndex >= 0) substring(separatorIndex + 2) else this
}

internal fun diffAgentThreadViewThreadOutlineModels(
  oldModel: AgentThreadViewThreadOutlineModel,
  newModel: AgentThreadViewThreadOutlineModel,
): AgentThreadViewThreadOutlineModelDiff {
  val rootChanged = oldModel.rootIds != newModel.rootIds
  val structureChangedIds = LinkedHashSet<AgentThreadViewThreadOutlineId>()
  val contentChangedIds = LinkedHashSet<AgentThreadViewThreadOutlineId>()
  oldModel.entriesById.forEach { (id, oldEntry) ->
    val newEntry = newModel.entriesById[id] ?: return@forEach
    if (oldEntry.childIds != newEntry.childIds) {
      structureChangedIds += id
    }
    if (oldEntry.node.presentationFingerprint() != newEntry.node.presentationFingerprint()) {
      contentChangedIds += id
    }
  }
  contentChangedIds.removeAll(structureChangedIds)
  return AgentThreadViewThreadOutlineModelDiff(
    rootChanged = rootChanged,
    structureChangedIds = structureChangedIds,
    contentChangedIds = contentChangedIds,
  )
}

internal fun AgentThreadViewVirtualFile.canShowAgentThreadViewThreadOutline(): Boolean {
  return !isPendingThread && validateAgentThreadViewFile(this) == null
}

private data class AgentThreadViewThreadOutlineNodePresentationFingerprint(
  @JvmField val title: String,
  @JvmField val timestamp: String?,
  @JvmField val location: String?,
  @JvmField val tooltip: String?,
  @JvmField val icon: Icon?,
)

private fun AgentThreadViewThreadOutlineNode.presentationFingerprint(): AgentThreadViewThreadOutlineNodePresentationFingerprint {
  return AgentThreadViewThreadOutlineNodePresentationFingerprint(
    title = title,
    timestamp = timestamp,
    location = location,
    tooltip = tooltip,
    icon = icon,
  )
}

internal fun AgentSessionOutlineItem.threadOutlineIcon(): Icon {
  return when (kind) {
    AgentSessionOutlineItemKind.USER_PROMPT -> AllIcons.General.User
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AllIcons.General.BalloonInformation
    AgentSessionOutlineItemKind.AGENT_WORK -> AllIcons.General.ExternalTools
    AgentSessionOutlineItemKind.TOOL_CALL -> AllIcons.Nodes.Console
    AgentSessionOutlineItemKind.TOOL_RESULT -> if (isSuccessfulToolResult()) AllIcons.General.InspectionsOK else AllIcons.General.Warning
    AgentSessionOutlineItemKind.PLAN -> AllIcons.Actions.Checked
    AgentSessionOutlineItemKind.APPROVAL_REQUEST -> AllIcons.General.Warning
    AgentSessionOutlineItemKind.INPUT_REQUEST -> AllIcons.General.QuestionDialog
    AgentSessionOutlineItemKind.SUMMARY,
    AgentSessionOutlineItemKind.METADATA,
      -> AllIcons.General.Information
  }
}

private fun AgentSessionOutlineItem.isSuccessfulToolResult(): Boolean {
  return title == "Exit 0" || !title.startsWith("Exit ")
}

private fun formatThreadOutlineTimestamp(timestamp: Long): String {
  return DateFormatUtil.formatPrettyDateTime(timestamp)
}

private fun formatThreadOutlineTimestampTooltip(timestamp: Long): String {
  return AgentThreadViewBundle.message("thread.view.thread.outline.timestamp", formatThreadOutlineTimestamp(timestamp))
}

private fun buildThreadOutlineTooltip(preview: String?, timestamp: String?): String? {
  return listOfNotNull(preview?.trim()?.takeIf { it.isNotEmpty() }, timestamp)
    .joinToString("\n")
    .takeIf { it.isNotEmpty() }
}

private fun compactThreadOutlineLocation(value: String?): String? {
  val location = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return if (location.length <= 96) location else location.take(93).trimEnd() + "..."
}

private fun AgentSessionOutlineItemKind.threadOutlineRolePrefix(): String? {
  return when (this) {
    AgentSessionOutlineItemKind.USER_PROMPT -> AgentThreadViewBundle.message("thread.view.thread.outline.role.user")
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AgentThreadViewBundle.message("thread.view.thread.outline.role.assistant")
    AgentSessionOutlineItemKind.AGENT_WORK,
    AgentSessionOutlineItemKind.TOOL_CALL,
    AgentSessionOutlineItemKind.TOOL_RESULT,
    AgentSessionOutlineItemKind.PLAN,
    AgentSessionOutlineItemKind.APPROVAL_REQUEST,
    AgentSessionOutlineItemKind.INPUT_REQUEST,
    AgentSessionOutlineItemKind.SUMMARY,
    AgentSessionOutlineItemKind.METADATA,
      -> null
  }
}

private fun AgentSessionOutlineItemKind.localizedThreadOutlineTitle(): String {
  return when (this) {
    AgentSessionOutlineItemKind.USER_PROMPT -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.user.prompt")
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.assistant.response")
    AgentSessionOutlineItemKind.AGENT_WORK -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.agent.work")
    AgentSessionOutlineItemKind.TOOL_CALL -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.tool.call")
    AgentSessionOutlineItemKind.TOOL_RESULT -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.tool.result")
    AgentSessionOutlineItemKind.PLAN -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.plan")
    AgentSessionOutlineItemKind.APPROVAL_REQUEST -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.approval.request")
    AgentSessionOutlineItemKind.INPUT_REQUEST -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.input.request")
    AgentSessionOutlineItemKind.SUMMARY -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.summary")
    AgentSessionOutlineItemKind.METADATA -> AgentThreadViewBundle.message("thread.view.thread.outline.kind.metadata")
  }
}
