// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import javax.swing.Icon

internal class JunieAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = JunieSessionSource(),
  private val executableResolver: suspend () -> String = JunieCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.JUNIE

  override val displayPriority: Int
    get() = 2

  override val displayNameKey: String
    get() = "toolwindow.provider.junie"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie.yolo"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Junie_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.junie.cli"

  override fun isCliAvailable(): Boolean = JunieCliSupport.isAvailable()

  override suspend fun ensureCliAvailable(): Boolean = JunieCliSupport.findExecutableViaTerminalResolver() != null

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildResumeCommand(sessionId = sessionId, executable = executableResolver()),
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildNewSessionCommand(yolo = mode == AgentSessionLaunchMode.YOLO, executable = executableResolver()),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? {
    val separator = identity.indexOf(':')
    if (separator <= 0 || separator == identity.lastIndex) {
      return null
    }
    if (!identity.substring(separator + 1).startsWith("new-")) {
      return null
    }
    if (AgentSessionProvider.fromOrNull(identity.substring(0, separator)) != AgentSessionProvider.JUNIE) {
      return null
    }
    return AgentPendingSessionMetadata(createdAtMs = System.currentTimeMillis(), launchMode = resolveLaunchMode(launchSpec))
  }
}

private fun resolveLaunchMode(launchSpec: AgentSessionTerminalLaunchSpec): String {
  return if (BRAVE_FLAG in launchSpec.command) AgentSessionLaunchMode.YOLO.name.lowercase()
  else AgentSessionLaunchMode.STANDARD.name.lowercase()
}
