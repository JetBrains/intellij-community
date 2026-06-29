// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.agent.workbench.engine.core.MessageRole
import com.intellij.agent.workbench.engine.core.RuntimeKind
import com.intellij.agent.workbench.engine.core.ThreadActionPrompt
import com.intellij.agent.workbench.engine.core.ThreadCommand
import com.intellij.agent.workbench.engine.core.ThreadContextCompaction
import com.intellij.agent.workbench.engine.core.ThreadFileDiff
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.core.ThreadMessage
import com.intellij.agent.workbench.engine.core.ThreadPlan
import com.intellij.agent.workbench.engine.core.ThreadProjection
import com.intellij.agent.workbench.engine.core.ThreadStatus
import com.intellij.agent.workbench.engine.core.ThreadToolCall
import com.intellij.agent.workbench.engine.core.ThreadTranscriptEntry
import com.intellij.agent.workbench.engine.platform.EventStore
import com.intellij.agent.workbench.engine.platform.EngineChangeBus
import com.intellij.agent.workbench.engine.platform.EngineUnreadTracker
import com.intellij.agent.workbench.engine.platform.EngineProjectService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Provider id under which all Engine-backed (ACP/remote/mock/…) threads surface in the existing UI. */
internal val ACP_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("acp")

/**
 * Surfaces Engine threads as `AgentSessionThread`s in the existing Agent Workbench tool
 * window, so ACP/remote/mock runtimes appear alongside Claude/Codex without a separate UI.
 */
internal class EngineSessionSource : AgentSessionSource, AgentSessionUpdateSource, AgentSessionThreadOutlineSource {
  override val provider: AgentSessionProvider
    get() = ACP_PROVIDER

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = EngineChangeBus.changes.map { change ->
      toSourceUpdateEvent(
        store = EngineProjectService.storeForPath(change.projectPath),
        projectPath = change.projectPath,
        threadId = change.threadId,
      )
    }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    if (openProject != null) {
      val tracker = EngineUnreadTracker.getInstance(openProject)
      return surfacedThreads(EngineProjectService.getInstance(openProject).eventStore) { tracker.isUnread(it) }
    }
    // No project instance -> no live unread state; restored threads are never badged.
    return surfacedThreads(EngineProjectService.storeForPath(path)) { false }
  }

  /**
   * Engine surfaces only its own native runtimes (ACP/remote/mock/structured-terminal). Plain
   * [RuntimeKind.Terminal] threads are legacy mirrors of existing CLI sessions and must not be
   * duplicated here, where they would render the IDE-native screen with no Engine data.
   */
  private fun surfacedThreads(store: EventStore, unread: (ThreadId) -> Boolean): List<AgentSessionThread> {
    return store.threadIds()
      .map { id -> store.projection(id) }
      .filter { it.thread.runtimeKind != RuntimeKind.Terminal }
      .map { toThread(it, unread(it.thread.id)) }
  }

  private fun toSourceUpdateEvent(
    store: EventStore,
    projectPath: String,
    threadId: ThreadId,
  ): AgentSessionSourceUpdateEvent {
    val projection = store.projection(threadId)
    return AgentSessionSourceUpdateEvent.threadsChanged(
      scopedPaths = setOf(projectPath),
      threadIds = setOf(threadId.value),
      presentationUpdatesByThreadId = mapOf(
        threadId.value to AgentSessionThreadPresentationUpdate(
          title = projection.thread.title.ifBlank { threadId.value },
          updatedAt = projection.thread.updatedAt,
        )
      ),
    )
  }

  override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline {
    val projection = EngineProjectService.storeForPath(path).projection(ThreadId(threadId))
    val items = projection.transcript.map { entry ->
      AgentSessionOutlineItem(
        id = entry.id,
        kind = outlineKind(entry),
        title = outlineTitle(entry).take(80),
        preview = outlinePreview(entry),
      )
    }
    return AgentSessionThreadOutline(
      provider = provider,
      threadId = threadId,
      title = projection.thread.title.ifBlank { threadId },
      updatedAt = projection.thread.updatedAt,
      items = items,
    )
  }

  private fun toThread(projection: ThreadProjection, unread: Boolean): AgentSessionThread {
    val thread = projection.thread
    return AgentSessionThread(
      id = thread.id.value,
      title = thread.title.ifBlank { thread.id.value },
      updatedAt = thread.updatedAt,
      archived = false,
      // A live, unseen agent reply gets the attention badge; otherwise fall back to the status mapping.
      activityReport = AgentThreadActivityReport(if (unread) AgentThreadActivity.NEEDS_INPUT else mapActivity(thread.status)),
      provider = provider,
    )
  }

  private fun mapActivity(status: ThreadStatus): AgentThreadActivity = when (status) {
    ThreadStatus.Running, ThreadStatus.Starting, ThreadStatus.Preparing -> AgentThreadActivity.PROCESSING
    // Only a genuinely blocking approval warrants the attention badge. "Waiting for user" is the idle
    // resting state (it's just the user's turn), so it stays unbadged — including for threads restored
    // from history, which must not light up with a blue dot.
    ThreadStatus.WaitingForApproval -> AgentThreadActivity.NEEDS_INPUT
    else -> AgentThreadActivity.READY
  }

  private fun outlineKind(entry: ThreadTranscriptEntry): AgentSessionOutlineItemKind = when (entry) {
    is ThreadMessage -> when (entry.role) {
      MessageRole.User -> AgentSessionOutlineItemKind.USER_PROMPT
      MessageRole.Agent -> AgentSessionOutlineItemKind.ASSISTANT_RESPONSE
      MessageRole.Tool -> AgentSessionOutlineItemKind.TOOL_CALL
      else -> AgentSessionOutlineItemKind.METADATA
    }
    is ThreadToolCall, is ThreadCommand -> AgentSessionOutlineItemKind.TOOL_CALL
    is ThreadPlan, is ThreadFileDiff, is ThreadContextCompaction, is ThreadActionPrompt -> AgentSessionOutlineItemKind.METADATA
  }

  private fun outlineTitle(entry: ThreadTranscriptEntry): String = when (entry) {
    is ThreadMessage -> entry.text.lineSequence().firstOrNull().orEmpty()
    is ThreadToolCall -> entry.title ?: entry.command ?: entry.kind ?: entry.id
    is ThreadCommand -> entry.title ?: entry.command ?: entry.id
    is ThreadPlan -> entry.title ?: entry.id
    is ThreadFileDiff -> entry.title ?: entry.path ?: entry.id
    is ThreadContextCompaction -> entry.title ?: entry.id
    is ThreadActionPrompt -> entry.title
  }

  private fun outlinePreview(entry: ThreadTranscriptEntry): String = when (entry) {
    is ThreadMessage -> entry.text
    is ThreadToolCall -> entry.outputText.ifBlank { entry.summary.orEmpty() }
    is ThreadCommand -> entry.outputText
    is ThreadPlan -> entry.items.joinToString(separator = "\n") { it.title }
    is ThreadFileDiff -> entry.newText.orEmpty()
    is ThreadContextCompaction -> entry.summary.orEmpty()
    is ThreadActionPrompt -> entry.message ?: entry.buttons.joinToString(separator = "\n") { it.text }
  }
}
