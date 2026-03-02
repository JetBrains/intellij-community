// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.registerShutdownOnCancellation
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Service(Service.Level.APP)
class SharedCodexAppServerService(serviceScope: CoroutineScope) {
  private val client = CodexAppServerClient(coroutineScope = serviceScope)

  init {
    registerShutdownOnCancellation(serviceScope) { client.shutdown() }
  }

  internal suspend fun listThreads(projectPath: Path): List<CodexThread> {
    val cwdFilter = normalizeRootPath(projectPath.invariantSeparatorsPathString)
    return client.listThreads(archived = false, cwdFilter = cwdFilter)
  }

  suspend fun createThread(cwd: String, yolo: Boolean): CodexThread {
    val thread = if (yolo) {
      client.createThread(cwd = cwd, approvalPolicy = "on-request", sandbox = "workspace-write")
    }
    else {
      client.createThread(cwd = cwd)
    }
    client.persistThread(thread.id)
    return thread
  }

  suspend fun archiveThread(threadId: String) {
    client.archiveThread(threadId)
  }

  suspend fun unarchiveThread(threadId: String) {
    client.unarchiveThread(threadId)
  }
}
