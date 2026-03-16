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
import com.intellij.openapi.components.service

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

  override fun isCliAvailable(): Boolean = CodexCliUtils.isAvailable()

  override fun buildResumeCommand(sessionId: String): List<String> = listOf(CodexCliUtils.CODEX_COMMAND, "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> {
    error("Codex new sessions use thread/start + resume, not direct CLI")
  }

  override fun buildNewEntryCommand(): List<String> = listOf(CodexCliUtils.CODEX_COMMAND)

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    val thread = service<SharedCodexAppServerService>().createThread(cwd = path, yolo = mode == AgentSessionLaunchMode.YOLO)
    return AgentSessionLaunchSpec(
      sessionId = thread.id,
      command = buildResumeCommand(thread.id),
    )
  }

  override fun isCliMissingError(throwable: Throwable): Boolean {
    return throwable is CodexCliNotFoundException
  }
}
