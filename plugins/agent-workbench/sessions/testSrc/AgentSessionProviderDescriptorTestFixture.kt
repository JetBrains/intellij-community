// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.openapi.project.Project
import javax.swing.Icon

open class TestAgentSessionProviderDescriptor(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val cliAvailable: Boolean,
  private val resumeEnvVariables: Map<String, String> = emptyMap(),
  private val iconOverride: Icon? = null,
  private val sourceId: String = provider.value,
  override val displayPriority: Int = Int.MAX_VALUE,
  override val yoloSessionLabelKey: String? = null,
  override val editorTabActionIds: List<String> = emptyList(),
  override val supportsPendingEditorTabRebind: Boolean = false,
  override val supportsNewThreadRebind: Boolean = false,
  override val supportsPromptLaunch: Boolean = true,
  private val threadRenameActionOverride: AgentThreadRenameAction? = null,
  override val emitsScopedRefreshSignals: Boolean = false,
  override val refreshPathAfterCreateNewSession: Boolean = false,
  override val archiveRefreshDelayMs: Long = 0L,
  override val suppressArchivedThreadsDuringRefresh: Boolean = false,
  override val supportsArchiveThread: Boolean = false,
  override val supportsUnarchiveThread: Boolean = false,
  override val providerSettings: List<AgentWorkbenchCheckboxSetting> = emptyList(),
  private val newSessionLabelKeyOverride: String? = null,
  override val quickStartActionTextKey: String = "action.AgentWorkbenchSessions.NewThreadQuick.text",
  override val quickStartActionDescriptionKey: String = "action.AgentWorkbenchSessions.NewThreadQuick.description",
  override val quickStartActionTargetDescriptionKey: String? = null,
  override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.PROMINENT,
  private val onCliAvailable: () -> Unit = {},
  private val newSessionLaunchSpecProvider: suspend (AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec = { mode ->
    AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  },
  private val archiveThreadHandler: suspend (String, String) -> Boolean = { _, _ -> false },
  private val unarchiveThreadHandler: suspend (String, String) -> Boolean = { _, _ -> false },
) : AgentSessionProviderDescriptor {
  override val displayNameKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.provider.claude" else "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = newSessionLabelKeyOverride
            ?: if (provider == AgentSessionProvider.CLAUDE) "toolwindow.action.new.session.claude" else "toolwindow.action.new.session.codex"

  override val icon: Icon
    get() = iconOverride
            ?: if (provider == AgentSessionProvider.CLAUDE) AgentWorkbenchCommonIcons.Claude else AgentWorkbenchCommonIcons.Codex

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@TestAgentSessionProviderDescriptor.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()

    override fun toString(): String = "TestAgentSessionSource($sourceId)"
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override suspend fun isCliAvailable(): Boolean {
    onCliAvailable()
    return cliAvailable
  }

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = listOf("test", "resume", sessionId),
      envVariables = resumeEnvVariables,
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return newSessionLaunchSpecProvider(mode)
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return archiveThreadHandler(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return unarchiveThreadHandler(path, threadId)
  }

  override val threadRenameAction: AgentThreadRenameAction?
    get() = threadRenameActionOverride
}
