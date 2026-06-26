// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Role of the author of a [ThreadMessage]. */
@Serializable
enum class MessageRole {
  User,
  Agent,
  System,
  Runtime,
  Tool,
}

/** One visible entry in the structured thread transcript. */
@Serializable
sealed interface ThreadTranscriptEntry {
  val id: String
  val updatedAt: Long
}

/** A single (possibly still streaming) message in a thread. */
@Serializable
data class ThreadMessage(
  override val id: String,
  val role: MessageRole,
  val text: String,
  val complete: Boolean,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry

@Serializable
data class ThreadToolOutput(
  val stream: String = "structured",
  val text: String,
)

@Serializable
enum class ThreadApprovalStatus {
  Requested,
  Approved,
  Rejected,
  Expired,
}

@Serializable
data class ThreadToolApproval(
  val toolCallId: String,
  val approvalId: String? = null,
  val title: String? = null,
  val kind: String? = null,
  val status: ThreadApprovalStatus = ThreadApprovalStatus.Requested,
  val reason: String? = null,
)

@Serializable
data class ThreadToolCall(
  override val id: String,
  val title: String? = null,
  val kind: String? = null,
  val command: String? = null,
  val path: String? = null,
  val terminalId: String? = null,
  val status: String? = null,
  val exitCode: Int? = null,
  val summary: String? = null,
  val output: List<ThreadToolOutput> = emptyList(),
  val approval: ThreadToolApproval? = null,
  val complete: Boolean = false,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry {
  val outputText: String
    get() = output.joinToString(separator = "") { it.text }
}

@Serializable
data class ThreadCommandOutput(
  val stream: String = "stdout",
  val text: String,
)

@Serializable
data class ThreadCommand(
  override val id: String,
  val title: String? = null,
  val command: String? = null,
  val status: String? = null,
  val exitCode: Int? = null,
  val output: List<ThreadCommandOutput> = emptyList(),
  val complete: Boolean = false,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry {
  val outputText: String
    get() = output.joinToString(separator = "") { it.text }
}

@Serializable
data class ThreadPlanItem(
  val id: String,
  val title: String,
  val status: String? = null,
  val priority: String? = null,
)

@Serializable
data class ThreadPlan(
  override val id: String,
  val title: String? = null,
  val items: List<ThreadPlanItem> = emptyList(),
  val complete: Boolean = false,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry

@Serializable
data class ThreadFileDiff(
  override val id: String,
  val path: String? = null,
  val title: String? = null,
  val status: String? = null,
  val oldText: String? = null,
  val newText: String? = null,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry

@Serializable
data class ThreadContextCompaction(
  override val id: String,
  val title: String? = null,
  val summary: String? = null,
  override val updatedAt: Long = 0L,
) : ThreadTranscriptEntry

@Serializable
data class ThreadUsage(
  val inputTokens: Long? = null,
  val outputTokens: Long? = null,
  val totalTokens: Long? = null,
  val costAmount: Double? = null,
  val costCurrency: String? = null,
)

@Serializable
data class ThreadCommandDescriptor(
  val id: String,
  val name: String,
  val description: String? = null,
)

@Serializable
data class ThreadConfigOption(
  val id: String,
  val name: String? = null,
  val value: String? = null,
)

/**
 * Reconnectable runtime binding for a structured thread (side state, not a transcript entry).
 * Seeded from `ThreadCreated` (`agentId`, `cwd`) and completed by `RuntimeSessionBound` once the
 * runtime returns a server session id. Used to decide whether a restored chat can reach its agent.
 */
@Serializable
data class ThreadRuntimeBinding(
  val runtimeKind: RuntimeKind,
  val agentId: String? = null,
  val agentSessionId: String? = null,
  val cwd: String? = null,
  val remoteBranch: String? = null,
  val organizationId: String? = null,
)

/** Materialized, immutable view of a thread, computed by [reduce] from its event log. */
@Serializable
data class AgentThread(
  val id: ThreadId,
  val title: String = "",
  val status: ThreadStatus = ThreadStatus.Draft,
  val runtimeKind: RuntimeKind = RuntimeKind.Terminal,
  val runtimeId: String? = null,
  val createdAt: Long = 0L,
  val updatedAt: Long = 0L,
  val summary: String? = null,
)

/**
 * Read model for a single thread. Reconstructable at any time by folding [reduce] over the event
 * log, so it can be rebuilt after an IDE restart and never holds runtime-specific shapes.
 */
@Serializable
data class ThreadProjection(
  val thread: AgentThread,
  val transcript: List<ThreadTranscriptEntry> = emptyList(),
  val usage: ThreadUsage? = null,
  val availableCommands: List<ThreadCommandDescriptor> = emptyList(),
  val mode: String? = null,
  val configOptions: Map<String, ThreadConfigOption> = emptyMap(),
  val runtimeBinding: ThreadRuntimeBinding? = null,
  val orphanPendingApprovals: Int = 0,
  val pendingToolOutputs: Map<String, List<ThreadToolOutput>> = emptyMap(),
  val pendingCommandOutputs: Map<String, List<ThreadCommandOutput>> = emptyMap(),
  val lastSeq: Long = -1L,
) {
  val messages: List<ThreadMessage>
    get() = transcript.filterIsInstance<ThreadMessage>()

  val pendingApprovals: Int
    get() = orphanPendingApprovals + transcript.count { entry ->
      entry is ThreadToolCall && entry.approval?.status == ThreadApprovalStatus.Requested
    }

  companion object {
    /** An empty projection for [threadId], used as the seed for [reduce]. */
    fun empty(threadId: ThreadId): ThreadProjection = ThreadProjection(AgentThread(id = threadId))
  }
}

/**
 * Pure reducer: applies a single [event] to [state] and returns the next projection.
 *
 * Stable-id updates are type-scoped. A late update for an unknown id is buffered or creates a matching
 * pending entry; it is never applied to a different visible entry with the same raw id.
 */
fun reduce(state: ThreadProjection, event: ThreadEventEnvelope): ThreadProjection {
  val payload = event.payload
  val withSeq = state.copy(lastSeq = event.seq)
  return when (event.type) {
    ThreadEventType.ThreadCreated -> withSeq.withThreadCreated(payload, event.timestamp)
    ThreadEventType.RuntimeSessionBound -> withSeq.withRuntimeBinding(payload, event.timestamp)
    ThreadEventType.ThreadStarted -> withSeq.withStatus(ThreadStatus.Running, event.timestamp)
    ThreadEventType.MessageDelta -> withSeq.withMessageDelta(payload, event.timestamp)
    ThreadEventType.MessageCompleted -> withSeq.withMessageCompleted(payload, event.timestamp)
    ThreadEventType.ToolCallStarted -> withSeq.withToolCallStarted(payload, event.timestamp)
    ThreadEventType.ToolCallOutput -> withSeq.withToolCallOutput(payload, event.timestamp)
    ThreadEventType.ToolCallFinished -> withSeq.withToolCallFinished(payload, event.timestamp)
    ThreadEventType.ApprovalRequested -> withSeq.withApprovalRequested(payload, event.timestamp)
    ThreadEventType.ApprovalResolved -> withSeq.withApprovalResolved(payload, event.timestamp)
    ThreadEventType.CommandStarted -> withSeq.withCommandStarted(payload, event.timestamp)
    ThreadEventType.CommandOutput -> withSeq.withCommandOutput(payload, event.timestamp)
    ThreadEventType.CommandFinished -> withSeq.withCommandFinished(payload, event.timestamp)
    ThreadEventType.PlanCreated, ThreadEventType.PlanUpdated -> withSeq.withPlan(payload, event.timestamp)
    ThreadEventType.DiffReady,
    ThreadEventType.FileChangeDetected,
    ThreadEventType.FileChangeProposed,
    ThreadEventType.FileChangeApplied,
      -> withSeq.withDiff(payload, event.type, event.timestamp)
    ThreadEventType.ContextCompacted, ThreadEventType.CheckpointCreated -> withSeq.withContextCompaction(payload, event.timestamp)
    ThreadEventType.UsageUpdated -> withSeq.withUsage(payload, event.timestamp)
    ThreadEventType.AvailableCommandsUpdated -> withSeq.withAvailableCommands(payload, event.timestamp)
    ThreadEventType.ModeChanged -> withSeq.withMode(payload, event.timestamp)
    ThreadEventType.ConfigOptionUpdated -> withSeq.withConfigOption(payload, event.timestamp)
    ThreadEventType.ThreadWaiting -> withSeq.withWaitingStatus(event.timestamp)
    ThreadEventType.ThreadCompleted -> withSeq.copy(
      thread = withSeq.thread.copy(
        status = ThreadStatus.Completed,
        summary = payload.string("summary") ?: withSeq.thread.summary,
        updatedAt = event.timestamp,
      ),
    )
    ThreadEventType.ThreadFailed -> withSeq.withStatus(ThreadStatus.Failed, event.timestamp)
    ThreadEventType.ThreadDisconnected -> withSeq.withStatus(ThreadStatus.Disconnected, event.timestamp)
    ThreadEventType.ThreadCancelled -> withSeq.withStatus(ThreadStatus.Cancelled, event.timestamp)
    else -> withSeq
  }
}

/** Folds [reduce] over an ordered sequence of events starting from [seed]. */
fun replay(seed: ThreadProjection, events: Iterable<ThreadEventEnvelope>): ThreadProjection =
  events.fold(seed, ::reduce)

private fun ThreadProjection.withStatus(status: ThreadStatus, at: Long): ThreadProjection =
  copy(thread = thread.copy(status = status, updatedAt = at))

private fun ThreadProjection.withThreadUpdated(at: Long): ThreadProjection =
  copy(thread = thread.copy(updatedAt = at))

private fun ThreadProjection.withWaitingStatus(at: Long): ThreadProjection =
  withStatus(if (pendingApprovals > 0) ThreadStatus.WaitingForApproval else ThreadStatus.WaitingForUser, at)

private fun ThreadProjection.withThreadCreated(payload: JsonObject, at: Long): ThreadProjection {
  val created = copy(
    thread = thread.copy(
      title = payload.string("title") ?: thread.title,
      runtimeKind = payload.enum("runtimeKind") ?: thread.runtimeKind,
      runtimeId = payload.string("runtimeId") ?: thread.runtimeId,
      status = ThreadStatus.Preparing,
      createdAt = at,
      updatedAt = at,
    ),
  )
  // ACP threads seed the reconnect binding (agentId/cwd) on creation; other runtimes carry no seed.
  return if (payload["agentId"] != null || payload["cwd"] != null) created.withRuntimeBinding(payload, at) else created
}

/**
 * Folds a binding event into [ThreadProjection.runtimeBinding] as side state. Repeated events merge
 * field-by-field and never overwrite a previously known value (in particular `agentSessionId`) with null.
 */
private fun ThreadProjection.withRuntimeBinding(payload: JsonObject, at: Long): ThreadProjection {
  val previous = runtimeBinding
  val kind = payload.enum<RuntimeKind>("runtimeKind") ?: previous?.runtimeKind ?: return withThreadUpdated(at)
  val merged = ThreadRuntimeBinding(
    runtimeKind = kind,
    agentId = payload.string("agentId") ?: previous?.agentId,
    agentSessionId = payload.string("agentSessionId") ?: previous?.agentSessionId,
    cwd = payload.string("cwd") ?: previous?.cwd,
    remoteBranch = payload.string("remoteBranch") ?: previous?.remoteBranch,
    organizationId = payload.string("organizationId") ?: previous?.organizationId,
  )
  return copy(runtimeBinding = merged).withThreadUpdated(at)
}

private fun ThreadProjection.withMessageDelta(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("messageId") ?: return this
  val delta = payload.string("contentDelta").orEmpty()
  val role = payload.enum<MessageRole>("role") ?: MessageRole.Agent
  val existing = transcript.indexOfFirst { it is ThreadMessage && it.id == id }
  val next = if (existing >= 0) {
    transcript.toMutableList().also {
      val m = it[existing] as ThreadMessage
      it[existing] = m.copy(role = role, text = m.text + delta, updatedAt = at)
    }
  }
  else {
    transcript + ThreadMessage(id = id, role = role, text = delta, complete = false, updatedAt = at)
  }
  return copy(transcript = next, thread = thread.copy(updatedAt = at))
}

private fun ThreadProjection.withMessageCompleted(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("messageId") ?: return this
  val idx = transcript.indexOfFirst { it is ThreadMessage && it.id == id }
  val next = if (idx >= 0) {
    transcript.toMutableList().also {
      val message = it[idx] as ThreadMessage
      it[idx] = message.copy(complete = true, updatedAt = at)
    }
  }
  else {
    transcript + ThreadMessage(id = id, role = MessageRole.Agent, text = "", complete = true, updatedAt = at)
  }
  return copy(transcript = next, thread = thread.copy(updatedAt = at))
}

private fun ThreadProjection.withToolCallStarted(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("toolCallId") ?: return this
  val bufferedOutput = pendingToolOutputs[id].orEmpty()
  val updated = updateToolCall(id, at, createIfMissing = true) { tool ->
    val status = payload.string("status") ?: tool.status
    tool.copy(
      title = payload.string("title") ?: tool.title,
      kind = payload.string("kind") ?: tool.kind,
      command = payload.string("command") ?: tool.command,
      path = payload.string("path") ?: tool.path,
      terminalId = payload.string("terminalId") ?: tool.terminalId,
      status = status,
      output = tool.output + bufferedOutput,
      complete = tool.complete || status.isFinishedStatus(),
      updatedAt = at,
    )
  }
  return updated.copy(pendingToolOutputs = updated.pendingToolOutputs - id).withThreadUpdated(at)
}

private fun ThreadProjection.withToolCallOutput(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("toolCallId") ?: return this
  val output = ThreadToolOutput(
    stream = payload.string("stream") ?: "structured",
    text = payload.string("contentDelta").orEmpty(),
  )
  val index = transcript.indexOfFirst { it is ThreadToolCall && it.id == id }
  if (index < 0) {
    return copy(pendingToolOutputs = pendingToolOutputs + (id to (pendingToolOutputs[id].orEmpty() + output))).withThreadUpdated(at)
  }
  return updateToolCall(id, at, createIfMissing = false) { tool ->
    tool.copy(output = tool.output + output, updatedAt = at)
  }.withThreadUpdated(at)
}

private fun ThreadProjection.withToolCallFinished(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("toolCallId") ?: return this
  val bufferedOutput = pendingToolOutputs[id].orEmpty()
  val updated = updateToolCall(id, at, createIfMissing = true) { tool ->
    val status = payload.string("status") ?: tool.status ?: "completed"
    tool.copy(
      status = status,
      exitCode = payload.int("exitCode") ?: tool.exitCode,
      summary = payload.string("summary") ?: tool.summary,
      output = tool.output + bufferedOutput,
      complete = true,
      updatedAt = at,
    )
  }
  return updated.copy(pendingToolOutputs = updated.pendingToolOutputs - id).withThreadUpdated(at)
}

private fun ThreadProjection.withApprovalRequested(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("toolCallId") ?: return copy(orphanPendingApprovals = orphanPendingApprovals + 1)
    .withStatus(ThreadStatus.WaitingForApproval, at)
  val approval = ThreadToolApproval(
    toolCallId = id,
    approvalId = payload.string("approvalId"),
    title = payload.string("title"),
    kind = payload.string("kind"),
    status = ThreadApprovalStatus.Requested,
  )
  return updateToolCall(id, at, createIfMissing = true) { tool ->
    tool.copy(
      title = payload.string("title") ?: tool.title,
      kind = payload.string("kind") ?: tool.kind,
      approval = approval,
      updatedAt = at,
    )
  }.withStatus(ThreadStatus.WaitingForApproval, at)
}

private fun ThreadProjection.withApprovalResolved(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("toolCallId") ?: return copy(orphanPendingApprovals = (orphanPendingApprovals - 1).coerceAtLeast(0))
    .withStatus(if (pendingApprovals <= 1) ThreadStatus.Running else ThreadStatus.WaitingForApproval, at)
  val decision = payload.string("decision").toApprovalStatus() ?: ThreadApprovalStatus.Approved
  val updated = updateToolCall(id, at, createIfMissing = true) { tool ->
    val previous = tool.approval
    tool.copy(
      approval = ThreadToolApproval(
        toolCallId = id,
        approvalId = payload.string("approvalId") ?: previous?.approvalId,
        title = previous?.title ?: payload.string("title") ?: tool.title,
        kind = previous?.kind ?: payload.string("kind") ?: tool.kind,
        status = decision,
        reason = payload.string("reason"),
      ),
      updatedAt = at,
    )
  }
  val nextStatus = if (updated.pendingApprovals > 0) {
    ThreadStatus.WaitingForApproval
  }
  else if (updated.thread.status == ThreadStatus.WaitingForApproval) {
    ThreadStatus.Running
  }
  else {
    updated.thread.status
  }
  return updated.withStatus(nextStatus, at)
}

private fun ThreadProjection.updateToolCall(
  id: String,
  at: Long,
  createIfMissing: Boolean,
  update: (ThreadToolCall) -> ThreadToolCall,
): ThreadProjection {
  val index = transcript.indexOfFirst { it is ThreadToolCall && it.id == id }
  if (index < 0) {
    return if (createIfMissing) {
      copy(transcript = transcript + update(ThreadToolCall(id = id, updatedAt = at)))
    }
    else {
      this
    }
  }
  val next = transcript.toMutableList()
  next[index] = update(next[index] as ThreadToolCall)
  return copy(transcript = next)
}

private fun ThreadProjection.withCommandStarted(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("terminalId") ?: return this
  val bufferedOutput = pendingCommandOutputs[id].orEmpty()
  val updated = updateCommand(id, at, createIfMissing = true) { command ->
    val status = payload.string("status") ?: command.status
    command.copy(
      title = payload.string("title") ?: command.title,
      command = payload.string("command") ?: command.command,
      status = status,
      output = command.output + bufferedOutput,
      complete = command.complete || status.isFinishedStatus(),
      updatedAt = at,
    )
  }
  return updated.copy(pendingCommandOutputs = updated.pendingCommandOutputs - id).withThreadUpdated(at)
}

private fun ThreadProjection.withCommandOutput(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("terminalId") ?: return this
  val output = ThreadCommandOutput(
    stream = payload.string("stream") ?: "stdout",
    text = payload.string("contentDelta").orEmpty(),
  )
  val index = transcript.indexOfFirst { it is ThreadCommand && it.id == id }
  if (index < 0) {
    return copy(pendingCommandOutputs = pendingCommandOutputs + (id to (pendingCommandOutputs[id].orEmpty() + output))).withThreadUpdated(at)
  }
  return updateCommand(id, at, createIfMissing = false) { command ->
    command.copy(output = command.output + output, updatedAt = at)
  }.withThreadUpdated(at)
}

private fun ThreadProjection.withCommandFinished(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("terminalId") ?: return this
  val bufferedOutput = pendingCommandOutputs[id].orEmpty()
  val updated = updateCommand(id, at, createIfMissing = true) { command ->
    val status = payload.string("status") ?: command.status ?: "completed"
    command.copy(
      status = status,
      exitCode = payload.int("exitCode") ?: command.exitCode,
      output = command.output + bufferedOutput,
      complete = true,
      updatedAt = at,
    )
  }
  return updated.copy(pendingCommandOutputs = updated.pendingCommandOutputs - id).withThreadUpdated(at)
}

private fun ThreadProjection.updateCommand(
  id: String,
  at: Long,
  createIfMissing: Boolean,
  update: (ThreadCommand) -> ThreadCommand,
): ThreadProjection {
  val index = transcript.indexOfFirst { it is ThreadCommand && it.id == id }
  if (index < 0) {
    return if (createIfMissing) copy(transcript = transcript + update(ThreadCommand(id = id, updatedAt = at))) else this
  }
  val next = transcript.toMutableList()
  next[index] = update(next[index] as ThreadCommand)
  return copy(transcript = next)
}

private fun ThreadProjection.withPlan(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("planId") ?: "plan"
  val items = payload.array("items") ?: payload.array("entries") ?: JsonArray(emptyList())
  val planItems = items.mapIndexedNotNull { index, item ->
    val itemObject = runCatching { item.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
    val title = itemObject.string("title") ?: itemObject.string("content") ?: return@mapIndexedNotNull null
    ThreadPlanItem(
      id = itemObject.string("id") ?: "$id:$index",
      title = title,
      status = itemObject.string("status"),
      priority = itemObject.string("priority"),
    )
  }
  val index = transcript.indexOfFirst { it is ThreadPlan && it.id == id }
  val complete = payload.boolean("complete") ?: planItems.isNotEmpty() && planItems.all { it.status.isFinishedStatus() }
  val entry = ThreadPlan(id = id, title = payload.string("title"), items = planItems, complete = complete, updatedAt = at)
  val next = if (index >= 0) transcript.toMutableList().also { it[index] = entry } else transcript + entry
  return copy(transcript = next).withThreadUpdated(at)
}

private fun ThreadProjection.withDiff(payload: JsonObject, type: ThreadEventType, at: Long): ThreadProjection {
  val id = payload.string("diffId") ?: payload.string("changeSetId") ?: payload.string("path") ?: return this
  val index = transcript.indexOfFirst { it is ThreadFileDiff && it.id == id }
  val existing = transcript.getOrNull(index) as? ThreadFileDiff
  val entry = ThreadFileDiff(
    id = id,
    path = payload.string("path") ?: existing?.path,
    title = payload.string("title") ?: existing?.title,
    status = payload.string("status") ?: type.name,
    oldText = payload.string("oldText") ?: existing?.oldText,
    newText = payload.string("newText") ?: existing?.newText,
    updatedAt = at,
  )
  val next = if (index >= 0) transcript.toMutableList().also { it[index] = entry } else transcript + entry
  return copy(transcript = next).withThreadUpdated(at)
}

private fun ThreadProjection.withContextCompaction(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("compactionId") ?: payload.string("checkpointId") ?: "context:$at"
  val index = transcript.indexOfFirst { it is ThreadContextCompaction && it.id == id }
  val entry = ThreadContextCompaction(id, payload.string("title"), payload.string("summary"), at)
  val next = if (index >= 0) transcript.toMutableList().also { it[index] = entry } else transcript + entry
  return copy(transcript = next).withThreadUpdated(at)
}

private fun ThreadProjection.withUsage(payload: JsonObject, at: Long): ThreadProjection = copy(
  usage = ThreadUsage(
    inputTokens = payload.long("inputTokens") ?: usage?.inputTokens,
    outputTokens = payload.long("outputTokens") ?: usage?.outputTokens,
    totalTokens = payload.long("totalTokens") ?: usage?.totalTokens,
    costAmount = payload.double("costAmount") ?: usage?.costAmount,
    costCurrency = payload.string("costCurrency") ?: usage?.costCurrency,
  ),
).withThreadUpdated(at)

private fun ThreadProjection.withAvailableCommands(payload: JsonObject, at: Long): ThreadProjection {
  val commands = payload.array("commands") ?: payload.array("availableCommands") ?: JsonArray(emptyList())
  return copy(
    availableCommands = commands.mapIndexedNotNull { index, command ->
      val obj = runCatching { command.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
      val name = obj.string("name") ?: return@mapIndexedNotNull null
      ThreadCommandDescriptor(
        id = obj.string("id") ?: name.ifBlank { "command:$index" },
        name = name,
        description = obj.string("description"),
      )
    },
  ).withThreadUpdated(at)
}

private fun ThreadProjection.withMode(payload: JsonObject, at: Long): ThreadProjection = copy(
  mode = payload.string("mode") ?: payload.string("modeId") ?: payload.string("currentModeId") ?: mode,
).withThreadUpdated(at)

private fun ThreadProjection.withConfigOption(payload: JsonObject, at: Long): ThreadProjection {
  val id = payload.string("configOptionId") ?: payload.string("optionId") ?: payload.string("id") ?: return this
  val option = ThreadConfigOption(
    id = id,
    name = payload.string("name") ?: configOptions[id]?.name,
    value = payload.string("value") ?: configOptions[id]?.value,
  )
  return copy(configOptions = configOptions + (id to option)).withThreadUpdated(at)
}

private fun JsonObject.string(key: String): String? = this[key]?.let {
  runCatching { it.jsonPrimitive.content }.getOrNull()
}

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

private fun JsonObject.double(key: String): Double? = string(key)?.toDoubleOrNull()

private fun JsonObject.boolean(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()

private fun JsonObject.array(key: String): JsonArray? = this[key]?.let {
  runCatching { it.jsonArray }.getOrNull()
}

private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T? {
  val raw = string(key) ?: return null
  return enumValues<T>().firstOrNull { it.name == raw }
}

private fun String?.isFinishedStatus(): Boolean {
  return equals("completed", ignoreCase = true) ||
         equals("failed", ignoreCase = true) ||
         equals("cancelled", ignoreCase = true)
}

private fun String?.toApprovalStatus(): ThreadApprovalStatus? = when {
  equals("approved", ignoreCase = true) -> ThreadApprovalStatus.Approved
  equals("rejected", ignoreCase = true) -> ThreadApprovalStatus.Rejected
  equals("expired", ignoreCase = true) -> ThreadApprovalStatus.Expired
  equals("requested", ignoreCase = true) -> ThreadApprovalStatus.Requested
  else -> null
}
