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
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

@Service(Service.Level.APP)
class SharedCodexAppServerService(serviceScope: CoroutineScope) {
  private val client = CodexWebSocketAppServerClient(
    coroutineScope = serviceScope,
  )
  private val prestartedPlanPromptSettings: MutableMap<String, CodexPlanPromptThreadSettings> = ConcurrentHashMap()

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

  internal suspend fun prestartPlanPromptThread(
    cwd: String,
    yolo: Boolean,
    model: String?,
  ): CodexPrestartedPlanPromptThread {
    val session = createPlanPromptThread(cwd = cwd, yolo = yolo, model = model)
    val threadId = session.thread.id
    try {
      prestartedPlanPromptSettings[threadId] = CodexPlanPromptThreadSettings(
        model = session.model,
      )
      client.materializeThread(threadId)
      return CodexPrestartedPlanPromptThread(
        threadId = threadId,
        remoteUrl = client.currentRemoteUrl(),
      )
    }
    catch (t: Throwable) {
      prestartedPlanPromptSettings.remove(threadId)
      runCatching { client.archiveThread(threadId) }
      throw t
    }
  }

  internal suspend fun startPlanPromptTurn(
    threadId: String,
    prompt: String,
    model: String?,
    reasoningEffort: String?,
  ) {
    val prestartedSettings = prestartedPlanPromptSettings[threadId]
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
    prestartedPlanPromptSettings.remove(threadId)
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

  private suspend fun createThreadInternal(cwd: String, yolo: Boolean) = if (yolo) {
    client.createThreadSession(cwd = cwd, approvalPolicy = "on-request", sandbox = "workspace-write")
  }
  else {
    client.createThreadSession(cwd = cwd)
  }

  private suspend fun createPlanPromptThread(cwd: String, yolo: Boolean, model: String?) = if (yolo) {
    client.createThreadSession(cwd = cwd, model = model, approvalPolicy = "on-request", sandbox = "workspace-write")
  }
  else {
    client.createThreadSession(cwd = cwd, model = model)
  }
}

internal data class CodexPrestartedPlanPromptThread(
  @JvmField val threadId: String,
  @JvmField val remoteUrl: String,
)

private data class CodexPlanPromptThreadSettings(
  @JvmField val model: String,
)

private const val CODEX_PLAN_COLLABORATION_MODE: String = "plan"
private const val CODEX_DEFAULT_PLAN_REASONING_EFFORT: String = "medium"
