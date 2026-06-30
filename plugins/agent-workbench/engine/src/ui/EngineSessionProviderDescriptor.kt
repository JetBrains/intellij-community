// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaces
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Registers the Engine-backed [EngineSessionSource] on the existing session-provider EP so Engine
 * threads show up in the Agent Workbench tool window. Read-only for now (no terminal launch); threads
 * are inspected via the role-aware outline.
 */
internal class EngineSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = EngineSessionSource(),
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = ACP_PROVIDER

  override val displayPriority: Int
    get() = 5

  override val displayNameKey: String
    get() = "toolwindow.provider.acp"

  override val displayNameFallback: String
    get() = "ACP"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.acp"

  override val icon: Icon
    get() = AllIcons.Toolwindows.ToolWindowMessages

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.acp.cli"

  override val supportsPromptLaunch: Boolean
    get() = true

  override val supportsDefaultLaunchProfile: Boolean
    get() = false

  override val supportsGenerationModelSelection: Boolean
    get() = false

  override val defaultLaunchSurface: AgentSessionSurfaceId
    get() = AgentSessionSurfaces.ACP

  override val supportedLaunchSurfaces: Set<AgentSessionSurfaceId>
    get() = setOf(AgentSessionSurfaces.ACP)

  override val resolvesGenerationModelCatalogForAutoSettings: Boolean
    get() = false

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec =
    AgentSessionTerminalLaunchSpec(command = emptyList(), useTerminalDefaultShell = true)

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec =
  // Preallocate the concrete Engine thread id so the threadView tab opens with it and the out-of-band ACP
    // launcher can prepare/send against the same id. No terminal: the tab renders custom content.
    AgentSessionTerminalLaunchSpec(
      command = emptyList(),
      useTerminalDefaultShell = true,
      preallocatedSessionId = "acp:" + java.util.UUID.randomUUID().toString().take(8),
    )

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan =
    AgentInitialMessagePlan.composeDefault(request)
}
