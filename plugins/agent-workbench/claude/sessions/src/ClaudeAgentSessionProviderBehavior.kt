// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBehavior
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import javax.swing.JComponent

internal class ClaudeAgentSessionProviderBehavior : AgentSessionProviderBehavior {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CLAUDE

  override fun onConversationOpened() {
    service<ClaudeQuotaHintStateService>().markEligible()
  }

  override fun createToolWindowNorthComponent(project: Project): JComponent {
    return ClaudeQuotaHintBanner()
  }
}

