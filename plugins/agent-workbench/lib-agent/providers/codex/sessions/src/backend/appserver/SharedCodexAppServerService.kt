// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions.backend.appserver

import com.intellij.platform.ai.agent.codex.common.CodexAppServerNotification
import com.intellij.platform.ai.agent.codex.common.CodexGenerationModel
import com.intellij.platform.ai.agent.codex.common.CodexSkill
import com.intellij.platform.ai.agent.codex.common.CodexThread
import com.intellij.platform.ai.agent.codex.common.CodexThreadActivitySnapshot
import com.intellij.platform.ai.agent.codex.common.CodexTurnCollaborationMode
import com.intellij.platform.ai.agent.codex.common.CodexWebSocketAppServerClient
import com.intellij.platform.ai.agent.codex.common.normalizeRootPath
import com.intellij.platform.ai.agent.codex.sessions.registerShutdownOnCancellation
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<SharedCodexAppServerService>()

@Service(Service.Level.APP)
class SharedCodexAppServerService(serviceScope: CoroutineScope) {
  private val client = CodexWebSocketAppServerClient(
    coroutineScope = serviceScope,
  )
  private val prestartedThreadSettings: MutableMap<String, CodexPrestartedThreadSettings> = ConcurrentHashMap()

  internal val notifications: Flow<CodexAppServerNotification>
    get() = client.notifications

  init {
    registerShutdownOnCancellation(serviceScope) { client.shutdown() }
  }

  internal suspend fun listThreads(projectPath: Path): List<CodexThread> {
    val cwdFilter = normalizeRootPath(projectPath.invariantSeparatorsPathString)
    return client.listThreads(archived = false, cwdFilter = cwdFilter)
  }

  internal suspend fun listArchivedThreads(projectPath: Path): List<CodexThread> {
    val cwdFilter = normalizeRootPath(projectPath.invariantSeparatorsPathString)
    return client.listThreads(archived = true, cwdFilter = cwdFilter)
  }

  internal suspend fun readThreadActivitySnapshot(threadId: String): CodexThreadActivitySnapshot? {
    return client.readThreadActivitySnapshot(threadId)
  }

  internal suspend fun readThread(threadId: String): CodexThread? {
    return client.readThread(threadId)
  }

  internal fun watchPathChanges(paths: Collection<Path>): Flow<Path> {
    // This is a direct bridge to Codex app-server fs/watch. Callers that need stronger
    // before-close append semantics, such as active rollout-file refresh on macOS, should merge
    // this flow with the immediate file watcher instead of relying on app-server alone.
    val watchPaths = paths.asSequence()
      .map { path -> path.toAbsolutePath().normalize() }
      .distinct()
      .toList()
    if (watchPaths.isEmpty()) {
      return emptyFlow()
    }
    LOG.debug("Starting Codex app-server file watches for ${watchPaths.size} paths")
    return merge(*watchPaths.map(client::watchPathChanges).toTypedArray())
  }

  internal suspend fun currentRemoteUrl(): String {
    return client.currentRemoteUrl()
  }

  internal suspend fun forkThread(threadId: String): CodexThread {
    return client.forkThread(threadId).thread
  }

  internal suspend fun rollbackThread(threadId: String, numTurns: Int): CodexThread? {
    return client.rollbackThread(threadId = threadId, numTurns = numTurns)
  }

  internal suspend fun listSkills(projectPath: Path): List<CodexSkill> {
    return client.listSkills(cwd = projectPath.invariantSeparatorsPathString)
  }

  internal suspend fun listModels(): List<CodexGenerationModel> {
    return client.listModels()
  }

  internal suspend fun prestartThread(
    cwd: String,
    yolo: Boolean,
    model: String?,
  ): CodexPrestartedThread {
    val session = createThreadInternal(cwd = cwd, yolo = yolo, model = model)
    val threadId = session.thread.id
    try {
      prestartedThreadSettings[threadId] = CodexPrestartedThreadSettings(
        model = session.model,
      )
      client.materializeThread(threadId)
      return CodexPrestartedThread(
        threadId = threadId,
        remoteUrl = client.currentRemoteUrl(),
      )
    }
    catch (t: Throwable) {
      prestartedThreadSettings.remove(threadId)
      runCatching { client.archiveThread(threadId) }
      throw t
    }
  }

  internal suspend fun startInitialPromptTurn(
    threadId: String,
    prompt: String,
    mode: AgentInitialMessageMode,
    model: String?,
    reasoningEffort: String?,
  ) {
    val prestartedSettings = prestartedThreadSettings[threadId]
    if (mode != AgentInitialMessageMode.PLAN) {
      client.startTurn(threadId = threadId, text = prompt)
      prestartedThreadSettings.remove(threadId)
      return
    }
    val effectiveModel = model ?: prestartedSettings?.model
    val collaborationMode = CodexTurnCollaborationMode(
      mode = CODEX_PLAN_COLLABORATION_MODE,
      model = effectiveModel,
      reasoningEffort = reasoningEffort ?: CODEX_DEFAULT_PLAN_REASONING_EFFORT,
      developerInstructions = null,
    )
    // Keep this pre-turn settings update: the resumed Codex TUI uses thread/settings/updated
    // to show the visible Plan mode footer before the prompt starts.
    client.updateThreadCollaborationMode(
      threadId = threadId,
      collaborationMode = collaborationMode,
    )
    client.startTurn(
      threadId = threadId,
      text = prompt,
      collaborationMode = collaborationMode,
    )
    prestartedThreadSettings.remove(threadId)
  }

  @Suppress("unused")
  suspend fun createThread(cwd: String, yolo: Boolean): CodexThread {
    val session = createThreadInternal(cwd = cwd, yolo = yolo)
    client.persistThread(session.thread.id)
    return session.thread
  }

  suspend fun archiveThread(threadId: String) {
    client.archiveThread(threadId)
  }

  suspend fun setThreadName(threadId: String, name: String) {
    client.setThreadName(threadId, name)
  }

  suspend fun unarchiveThread(threadId: String) {
    client.unarchiveThread(threadId)
  }

  private suspend fun createThreadInternal(cwd: String, yolo: Boolean, model: String? = null) = if (yolo) {
    client.createThreadSession(cwd = cwd, model = model, approvalPolicy = "on-request", sandbox = "workspace-write")
  }
  else {
    client.createThreadSession(cwd = cwd, model = model)
  }
}

internal data class CodexPrestartedThread(
  @JvmField val threadId: String,
  @JvmField val remoteUrl: String,
)

private data class CodexPrestartedThreadSettings(
  @JvmField val model: String,
)

private const val CODEX_PLAN_COLLABORATION_MODE: String = "plan"
private const val CODEX_DEFAULT_PLAN_REASONING_EFFORT: String = "medium"
