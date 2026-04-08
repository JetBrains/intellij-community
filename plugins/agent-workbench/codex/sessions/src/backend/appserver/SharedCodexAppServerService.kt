// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexWebSocketAppServerClient
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.registerShutdownOnCancellation
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Service(Service.Level.APP)
class SharedCodexAppServerService(serviceScope: CoroutineScope) {
  private val client = CodexWebSocketAppServerClient(
    coroutineScope = serviceScope,
  )

  internal val notifications: Flow<CodexAppServerNotification>
    get() = client.notifications

  init {
    registerShutdownOnCancellation(serviceScope) { client.shutdown() }
  }

  internal suspend fun listThreads(projectPath: Path): List<CodexThread> {
    val cwdFilter = normalizeRootPath(projectPath.invariantSeparatorsPathString)
    return client.listThreads(archived = false, cwdFilter = cwdFilter)
  }

  internal suspend fun readThreadActivitySnapshot(threadId: String): CodexThreadActivitySnapshot? {
    return client.readThreadActivitySnapshot(threadId)
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
}
