// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBehavior
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec

internal class CodexAgentSessionProviderBehavior : AgentSessionProviderBehavior {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val editorTabActionIds: List<String>
    get() = listOf(AgentWorkbenchActionIds.Sessions.BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB)

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val supportsNewThreadRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? {
    val separator = identity.indexOf(':')
    if (separator <= 0 || separator == identity.lastIndex) {
      return null
    }
    if (identity.substring(separator + 1).startsWith("new-").not()) {
      return null
    }
    if (AgentSessionProvider.from(identity.substring(0, separator)) != AgentSessionProvider.CODEX) {
      return null
    }
    return AgentPendingSessionMetadata(
      createdAtMs = System.currentTimeMillis(),
      launchMode = if ("--full-auto" in launchSpec.command) "yolo" else "standard",
    )
  }
}
