// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.DateFormatUtil
import javax.swing.Icon

const val AGENT_CHAT_THREAD_OUTLINE_TOOL_WINDOW_ID: String = "agent.workbench.chat.thread.outline"

internal object AgentChatThreadOutlineDataKeys {
  @JvmField
  val SELECTED_TARGET: DataKey<AgentChatThreadOutlineTarget> = DataKey.create("agent.workbench.chat.thread.outline.selectedTarget")
}

internal data class AgentChatThreadOutlineTarget(
  @JvmField val file: AgentChatVirtualFile,
  @JvmField val source: AgentSessionSource,
  @JvmField val item: AgentSessionOutlineItem,
)

internal data class AgentChatThreadOutlineModel(
  @JvmField val rootIds: List<AgentChatThreadOutlineId>,
  @JvmField val entriesById: Map<AgentChatThreadOutlineId, AgentChatThreadOutlineEntry>,
  @JvmField val autoExpandIds: List<AgentChatThreadOutlineId> = emptyList(),
) {
  companion object {
    val EMPTY: AgentChatThreadOutlineModel = status(
      title = AgentChatBundle.message("chat.thread.outline.no.selection"),
      status = null,
      icon = null,
    )

    fun status(
      title: @NlsSafe String,
      status: @NlsSafe String?,
      icon: Icon?,
    ): AgentChatThreadOutlineModel {
      val id = AgentChatThreadOutlineId.Status
      return AgentChatThreadOutlineModel(
        rootIds = listOf(id),
        entriesById = mapOf(
          id to AgentChatThreadOutlineEntry(
            id = id,
            parentId = null,
            node = AgentChatThreadOutlineNode.Status(
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

internal data class AgentChatThreadOutlineEntry(
  @JvmField val id: AgentChatThreadOutlineId,
  @JvmField val parentId: AgentChatThreadOutlineId?,
  @JvmField val node: AgentChatThreadOutlineNode,
  @JvmField val childIds: List<AgentChatThreadOutlineId> = emptyList(),
)

internal data class AgentChatThreadOutlineModelDiff(
  @JvmField val rootChanged: Boolean,
  @JvmField val structureChangedIds: Set<AgentChatThreadOutlineId>,
  @JvmField val contentChangedIds: Set<AgentChatThreadOutlineId>,
)

internal sealed interface AgentChatThreadOutlineId {
  data object Status : AgentChatThreadOutlineId
  data class Item(@JvmField val ordinalPath: String) : AgentChatThreadOutlineId
}

internal sealed interface AgentChatThreadOutlineNode {
  val title: @NlsSafe String
  val timestamp: @NlsSafe String?
  val location: @NlsSafe String?
  val tooltip: @NlsSafe String?
  val icon: Icon?
  val target: AgentChatThreadOutlineTarget?

  data class Status(
    override val title: @NlsSafe String,
    @JvmField val status: @NlsSafe String?,
    override val icon: Icon?,
  ) : AgentChatThreadOutlineNode {
    override val timestamp: @NlsSafe String? get() = null
    override val location: @NlsSafe String? get() = status
    override val tooltip: @NlsSafe String? get() = status
    override val target: AgentChatThreadOutlineTarget? get() = null
  }

  data class Item(
    override val title: @NlsSafe String,
    override val timestamp: @NlsSafe String?,
    override val location: @NlsSafe String?,
    override val tooltip: @NlsSafe String?,
    override val icon: Icon?,
    override val target: AgentChatThreadOutlineTarget,
  ) : AgentChatThreadOutlineNode
}

internal fun buildAgentChatThreadOutlineModel(
  file: AgentChatVirtualFile,
  source: AgentSessionSource,
  outline: AgentSessionThreadOutline,
): AgentChatThreadOutlineModel {
  if (outline.items.isEmpty()) {
    return AgentChatThreadOutlineModel.status(
      title = outline.title,
      status = AgentChatBundle.message("chat.thread.outline.empty"),
      icon = providerIcon(provider = outline.provider),
    )
  }

  val entriesById = LinkedHashMap<AgentChatThreadOutlineId, AgentChatThreadOutlineEntry>()
  val rootIds = outline.items.mapIndexed { index, item ->
    addAgentChatThreadOutlineItem(
      entriesById = entriesById,
      file = file,
      source = source,
      item = item,
      parentId = null,
      ordinalPath = index.toString(),
    )
  }
  return AgentChatThreadOutlineModel(
    rootIds = rootIds,
    entriesById = entriesById,
    autoExpandIds = if (rootIds.size == 1) rootIds else emptyList(),
  )
}

private fun addAgentChatThreadOutlineItem(
  entriesById: MutableMap<AgentChatThreadOutlineId, AgentChatThreadOutlineEntry>,
  file: AgentChatVirtualFile,
  source: AgentSessionSource,
  item: AgentSessionOutlineItem,
  parentId: AgentChatThreadOutlineId?,
  ordinalPath: String,
): AgentChatThreadOutlineId {
  val id = AgentChatThreadOutlineId.Item(ordinalPath)
  val childIds = item.children.mapIndexed { index, child ->
    addAgentChatThreadOutlineItem(
      entriesById = entriesById,
      file = file,
      source = source,
      item = child,
      parentId = id,
      ordinalPath = "$ordinalPath/$index",
    )
  }
  entriesById[id] = AgentChatThreadOutlineEntry(
    id = id,
    parentId = parentId,
    node = item.toAgentChatThreadOutlineNode(file = file, source = source),
    childIds = childIds,
  )
  return id
}

private fun AgentSessionOutlineItem.toAgentChatThreadOutlineNode(
  file: AgentChatVirtualFile,
  source: AgentSessionSource,
): AgentChatThreadOutlineNode.Item {
  val timestamp = timestampMs?.let(::formatThreadOutlineTimestamp)
  val title = threadOutlineTitle()
  val location = compactThreadOutlineLocation(preview).takeIf { location -> location != title.threadOutlineTitleContent() }
  return AgentChatThreadOutlineNode.Item(
    title = title,
    timestamp = timestamp,
    location = location,
    tooltip = buildThreadOutlineTooltip(
      preview = preview,
      timestamp = timestampMs?.let(::formatThreadOutlineTimestampTooltip),
    ),
    icon = threadOutlineIcon(),
    target = AgentChatThreadOutlineTarget(file = file, source = source, item = this),
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

internal fun diffAgentChatThreadOutlineModels(
  oldModel: AgentChatThreadOutlineModel,
  newModel: AgentChatThreadOutlineModel,
): AgentChatThreadOutlineModelDiff {
  val rootChanged = oldModel.rootIds != newModel.rootIds
  val structureChangedIds = LinkedHashSet<AgentChatThreadOutlineId>()
  val contentChangedIds = LinkedHashSet<AgentChatThreadOutlineId>()
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
  return AgentChatThreadOutlineModelDiff(
    rootChanged = rootChanged,
    structureChangedIds = structureChangedIds,
    contentChangedIds = contentChangedIds,
  )
}

internal fun AgentChatVirtualFile.canShowAgentChatThreadOutline(): Boolean {
  return !isPendingThread && validateAgentChatFile(this) == null
}

private data class AgentChatThreadOutlineNodePresentationFingerprint(
  @JvmField val title: String,
  @JvmField val timestamp: String?,
  @JvmField val location: String?,
  @JvmField val tooltip: String?,
  @JvmField val icon: Icon?,
)

private fun AgentChatThreadOutlineNode.presentationFingerprint(): AgentChatThreadOutlineNodePresentationFingerprint {
  return AgentChatThreadOutlineNodePresentationFingerprint(
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
  return AgentChatBundle.message("chat.thread.outline.timestamp", formatThreadOutlineTimestamp(timestamp))
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
    AgentSessionOutlineItemKind.USER_PROMPT -> AgentChatBundle.message("chat.thread.outline.role.user")
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AgentChatBundle.message("chat.thread.outline.role.assistant")
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
    AgentSessionOutlineItemKind.USER_PROMPT -> AgentChatBundle.message("chat.thread.outline.kind.user.prompt")
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AgentChatBundle.message("chat.thread.outline.kind.assistant.response")
    AgentSessionOutlineItemKind.AGENT_WORK -> AgentChatBundle.message("chat.thread.outline.kind.agent.work")
    AgentSessionOutlineItemKind.TOOL_CALL -> AgentChatBundle.message("chat.thread.outline.kind.tool.call")
    AgentSessionOutlineItemKind.TOOL_RESULT -> AgentChatBundle.message("chat.thread.outline.kind.tool.result")
    AgentSessionOutlineItemKind.PLAN -> AgentChatBundle.message("chat.thread.outline.kind.plan")
    AgentSessionOutlineItemKind.APPROVAL_REQUEST -> AgentChatBundle.message("chat.thread.outline.kind.approval.request")
    AgentSessionOutlineItemKind.INPUT_REQUEST -> AgentChatBundle.message("chat.thread.outline.kind.input.request")
    AgentSessionOutlineItemKind.SUMMARY -> AgentChatBundle.message("chat.thread.outline.kind.summary")
    AgentSessionOutlineItemKind.METADATA -> AgentChatBundle.message("chat.thread.outline.kind.metadata")
  }
}
