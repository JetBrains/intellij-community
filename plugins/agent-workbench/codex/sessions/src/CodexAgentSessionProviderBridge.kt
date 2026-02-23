// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.sessions.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionProviderIconIds
import com.intellij.agent.workbench.sessions.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.providers.AgentSessionSource
import com.intellij.openapi.components.serviceAsync

internal class CodexAgentSessionProviderBridge(
  override val sessionSource: AgentSessionSource = CodexSessionSource(),
) : AgentSessionProviderBridge {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex.yolo"

  override val iconId: String
    get() = AgentSessionProviderIconIds.CODEX

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override fun isCliAvailable(): Boolean = CodexCliUtils.findExecutable() != null

  override fun buildResumeCommand(sessionId: String): List<String> = listOf(CodexCliUtils.CODEX_COMMAND, "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> {
    return if (mode == AgentSessionLaunchMode.YOLO) {
      listOf(CodexCliUtils.CODEX_COMMAND, "--full-auto")
    }
    else {
      listOf(CodexCliUtils.CODEX_COMMAND)
    }
  }

  override fun buildNewEntryCommand(): List<String> = listOf(CodexCliUtils.CODEX_COMMAND)

  @Suppress("UNUSED_PARAMETER")
  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      command = buildNewSessionCommand(mode),
    )
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    serviceAsync<SharedCodexAppServerService>().archiveThread(threadId)
    return true
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    serviceAsync<SharedCodexAppServerService>().unarchiveThread(threadId)
    return true
  }

  override fun isCliMissingError(throwable: Throwable): Boolean {
    return throwable is CodexCliNotFoundException
  }
}
