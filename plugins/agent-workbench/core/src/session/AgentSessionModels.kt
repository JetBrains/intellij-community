// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.core.session

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.core.AgentThreadActivity
import com.intellij.agent.workbench.core.AgentThreadActivityReport
import com.intellij.openapi.util.NlsSafe
import java.util.IdentityHashMap

private val AGENT_SESSION_PROVIDER_ID_REGEX = Regex("[a-z][a-z0-9._-]*")
private val AGENT_SESSION_OUTLINE_WHITESPACE = Regex("\\s+")

@JvmInline
value class AgentSessionProvider private constructor(val value: String) {
  companion object {
    val CODEX: AgentSessionProvider = from("codex")

    val CLAUDE: AgentSessionProvider = from("claude")

    val JUNIE: AgentSessionProvider = from("junie")

    val OPENCODE: AgentSessionProvider = from("opencode")

    val PI: AgentSessionProvider = from("pi")

    val TERMINAL: AgentSessionProvider = from("terminal")

    fun from(value: String): AgentSessionProvider {
      require(AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) {
        "Invalid provider id '$value'. Expected: ${AGENT_SESSION_PROVIDER_ID_REGEX.pattern}"
      }
      return AgentSessionProvider(value)
    }

    fun fromOrNull(value: String): AgentSessionProvider? {
      return if (AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) AgentSessionProvider(value) else null
    }
  }

  override fun toString(): String = value
}

enum class AgentSessionLaunchMode {
  STANDARD,
  YOLO,
}

data class AgentSubAgent(
  @JvmField val id: @NlsSafe String,
  @JvmField val name: @NlsSafe String,
  @JvmField val activity: AgentThreadActivity = AgentThreadActivity.READY,
)

data class AgentSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean,
  @JvmField val activityReport: AgentThreadActivityReport = AgentThreadActivityReport.READY,
  val provider: AgentSessionProvider,
  @JvmField val subAgents: List<AgentSubAgent> = emptyList(),
  @JvmField val originBranch: String? = null,
  @JvmField val cost: AgentSessionCost? = null,
) {
  constructor(
    id: String,
    title: String,
    updatedAt: Long,
    archived: Boolean,
    activity: AgentThreadActivity,
    provider: AgentSessionProvider,
    subAgents: List<AgentSubAgent> = emptyList(),
    originBranch: String? = null,
    summaryActivity: AgentThreadActivity? = activity,
    cost: AgentSessionCost? = null,
  ) : this(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activityReport = AgentThreadActivityReport(rowActivity = activity, chromeActivity = summaryActivity),
    provider = provider,
    subAgents = subAgents,
    originBranch = originBranch,
    cost = cost,
  )

  val activity: AgentThreadActivity
    get() = activityReport.rowActivity

  val summaryActivity: AgentThreadActivity?
    get() = activityReport.chromeActivity
}

/**
 * Provider-neutral outline for a persisted agent thread.
 *
 * This is a role-aware conversation outline, not a dump of provider-specific log records. [items] are the visible roots in provider
 * history order. Providers should hide bookkeeping containers and expose user prompts, assistant responses, tool activity, plans,
 * approvals, and summaries with the closest [AgentSessionOutlineItemKind]. A `null` outline from a source means the provider cannot
 * load an outline for the thread; an empty [items] list means the outline is supported but has no visible entries.
 */
data class AgentSessionThreadOutline(
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val items: List<AgentSessionOutlineItem>,
) {
  val sessionId: String
    get() = threadId
}

/**
 * Visible item in an [AgentSessionThreadOutline].
 *
 * [id] must be unique within the outline and stable across reloads for the same provider record. If navigation or fork actions are
 * enabled for this item, [id] must be a provider/backend anchor rather than a UI ordinal. [kind] is semantic role data and should be
 * mapped from native provider roles, not chosen for presentation only. [title] is the primary row label; [preview] is supporting text
 * and may contain the message body when the title is intentionally blank. [children] describe ownership or causality, for example tool
 * calls and results under the assistant/work item that produced them.
 */
data class AgentSessionOutlineItem(
  @JvmField val id: String,
  @JvmField val kind: AgentSessionOutlineItemKind,
  @JvmField val title: String,
  @JvmField val preview: String? = null,
  @JvmField val timestampMs: Long? = null,
  @JvmField val children: List<AgentSessionOutlineItem> = emptyList(),
) {
  val timestampMillis: Long?
    get() = timestampMs
}

/**
 * Semantic role of an [AgentSessionOutlineItem].
 *
 * Providers should map native transcript roles to these values consistently so shared UI and actions can reason about the outline:
 * user-authored prompts use [USER_PROMPT], assistant text uses [ASSISTANT_RESPONSE], provider processing phases use [AGENT_WORK],
 * invocations use [TOOL_CALL], their outputs use [TOOL_RESULT], and internal or unknown records should be skipped or mapped to
 * [METADATA] instead of leaking raw provider event names.
 */
enum class AgentSessionOutlineItemKind {
  USER_PROMPT,
  ASSISTANT_RESPONSE,
  AGENT_WORK,
  TOOL_CALL,
  TOOL_RESULT,
  PLAN,
  APPROVAL_REQUEST,
  INPUT_REQUEST,
  SUMMARY,
  METADATA,
}

/** Mutable helper for constructing nested [AgentSessionOutlineItem] trees. */
class AgentSessionOutlineItemBuilder(
  @JvmField val id: String,
  @JvmField var kind: AgentSessionOutlineItemKind,
  @JvmField var title: String,
  @JvmField var preview: String?,
  @JvmField val timestampMs: Long?,
  @JvmField val summarizesChildren: Boolean = false,
  @JvmField val children: MutableList<AgentSessionOutlineItemBuilder> = ArrayList(),
) {
  fun build(): AgentSessionOutlineItem {
    val builtChildren = children.map(AgentSessionOutlineItemBuilder::build)
    return AgentSessionOutlineItem(
      id = id,
      kind = kind,
      title = title,
      preview = if (summarizesChildren && builtChildren.isNotEmpty()) summarizeAgentSessionOutlineChildren(builtChildren) else preview,
      timestampMs = timestampMs,
      children = builtChildren,
    )
  }
}

/**
 * Flat source record used by [buildAgentSessionOutlineTree].
 *
 * [parentId] links provider records before they are converted to visible outline items. Set [visible] to `false` for bookkeeping
 * containers such as raw turns or sessions; their visible descendants are promoted to the nearest visible ancestor or to the root.
 * Records with missing, blank, or self-referential parents become roots. Duplicate ids keep the first record.
 */
data class AgentSessionOutlineTreeRecord(
  @JvmField val id: String,
  @JvmField val parentId: String?,
  @JvmField val kind: AgentSessionOutlineItemKind,
  @JvmField val title: String,
  @JvmField val preview: String? = null,
  @JvmField val timestampMs: Long? = null,
  @JvmField val visible: Boolean = true,
)

/**
 * Builds a visible outline tree from flat provider records.
 *
 * Returned roots and siblings are ordered by [AgentSessionOutlineTreeRecord.timestampMs] when present, then by input order. Invisible
 * records are not returned, but their visible descendants are preserved and promoted. This lets providers keep raw parent/child
 * relationships for future navigation while presenting a flat, role-aware conversation timeline.
 */
fun buildAgentSessionOutlineTree(records: List<AgentSessionOutlineTreeRecord>): List<AgentSessionOutlineItem> {
  if (records.isEmpty()) {
    return emptyList()
  }

  val nodesById = LinkedHashMap<String, AgentSessionOutlineTreeNode>(records.size)
  records.forEachIndexed { index, record ->
    val id = record.id.trim().takeIf { it.isNotEmpty() } ?: return@forEachIndexed
    nodesById.putIfAbsent(id, AgentSessionOutlineTreeNode(record = record, order = index))
  }

  val roots = ArrayList<AgentSessionOutlineTreeNode>()
  for (node in nodesById.values) {
    val parentId = node.record.parentId?.trim()?.takeIf { it.isNotEmpty() && it != node.record.id }
    val parent = parentId?.let(nodesById::get)
    if (parent == null) {
      roots += node
    }
    else {
      parent.children += node
    }
  }
  return buildVisibleOutlineItems(roots)
}

fun normalizeAgentSessionOutlinePreview(value: String?, maxLength: Int = 160): String? {
  val normalized = value
                     ?.replace('\n', ' ')
                     ?.replace('\r', ' ')
                     ?.replace(AGENT_SESSION_OUTLINE_WHITESPACE, " ")
                     ?.trim()
                     ?.takeIf { it.isNotEmpty() }
                   ?: return null
  return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 3).trimEnd() + "..."
}

fun compactAgentSessionOutlineText(value: String?, maxLength: Int): String? {
  return normalizeAgentSessionOutlinePreview(value, maxLength = maxLength)
}

fun agentSessionOutlinePhaseTitle(preview: String?, maxLength: Int = 90): String? {
  val normalizedPreview = normalizeAgentSessionOutlinePreview(preview) ?: return null
  val sentenceEnd = normalizedPreview.indexOfAny(charArrayOf('.', '!', '?'))
  val firstSentence = if (sentenceEnd > 0) normalizedPreview.take(sentenceEnd + 1) else normalizedPreview
  return firstSentence.take(maxLength).trimEnd().takeIf { it.isNotEmpty() }
}

fun dedupeAgentSessionOutlineText(value: String): String {
  return value.replace(AGENT_SESSION_OUTLINE_WHITESPACE, " ").trim().lowercase()
}

fun summarizeAgentSessionOutlineChildren(children: List<AgentSessionOutlineItem>): String? {
  val parts = ArrayList<String>(6)
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.TOOL_CALL }, "tool")
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.TOOL_RESULT }, "result")
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.PLAN }, "plan")
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.APPROVAL_REQUEST }, "approval")
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.INPUT_REQUEST }, "input request")
  addAgentSessionOutlineCount(parts, children.count { it.kind == AgentSessionOutlineItemKind.AGENT_WORK }, "work item")
  return parts.joinToString(", ").takeIf { it.isNotEmpty() }
}

private fun buildVisibleOutlineItems(roots: List<AgentSessionOutlineTreeNode>): List<AgentSessionOutlineItem> {
  val builtByNode = IdentityHashMap<AgentSessionOutlineTreeNode, List<AgentSessionOutlineItem>>()
  val stack = ArrayDeque<AgentSessionOutlineTreeStackFrame>()
  roots.sortedWith(AGENT_SESSION_OUTLINE_TREE_NODE_COMPARATOR).asReversed().forEach { root ->
    stack.addLast(AgentSessionOutlineTreeStackFrame(root, childrenVisited = false))
  }

  while (stack.isNotEmpty()) {
    val frame = stack.removeLast()
    val node = frame.node
    if (!frame.childrenVisited) {
      stack.addLast(AgentSessionOutlineTreeStackFrame(node, childrenVisited = true))
      node.children.sortedWith(AGENT_SESSION_OUTLINE_TREE_NODE_COMPARATOR).asReversed().forEach { child ->
        stack.addLast(AgentSessionOutlineTreeStackFrame(child, childrenVisited = false))
      }
      continue
    }

    val builtChildren = node.children.sortedWith(AGENT_SESSION_OUTLINE_TREE_NODE_COMPARATOR)
      .flatMap { child -> builtByNode.remove(child).orEmpty() }
    val record = node.record
    builtByNode[node] = if (record.visible) {
      listOf(
        AgentSessionOutlineItem(
          id = record.id,
          kind = record.kind,
          title = record.title,
          preview = normalizeAgentSessionOutlinePreview(record.preview),
          timestampMs = record.timestampMs,
          children = builtChildren,
        )
      )
    }
    else {
      builtChildren
    }
  }

  return roots.sortedWith(AGENT_SESSION_OUTLINE_TREE_NODE_COMPARATOR)
    .flatMap { root -> builtByNode[root].orEmpty() }
}

private fun addAgentSessionOutlineCount(parts: MutableList<String>, count: Int, label: String) {
  if (count <= 0) {
    return
  }
  parts += "$count ${if (count == 1) label else label + "s"}"
}

private data class AgentSessionOutlineTreeNode(
  @JvmField val record: AgentSessionOutlineTreeRecord,
  @JvmField val order: Int,
  @JvmField val children: MutableList<AgentSessionOutlineTreeNode> = ArrayList(),
)

private data class AgentSessionOutlineTreeStackFrame(
  @JvmField val node: AgentSessionOutlineTreeNode,
  @JvmField val childrenVisited: Boolean,
)

private val AGENT_SESSION_OUTLINE_TREE_NODE_COMPARATOR = compareBy<AgentSessionOutlineTreeNode>(
  { node -> node.record.timestampMs ?: Long.MAX_VALUE },
  { node -> node.order },
)
