// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderUiContributor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import javax.swing.JComponent

internal class ClaudeQuotaSessionProviderUiContributor : AgentSessionProviderUiContributor {
  override fun onThreadViewOpened() {
    service<ClaudeQuotaHintStateService>().markEligible()
  }

  override fun createToolWindowNorthComponent(project: Project): JComponent {
    return ClaudeQuotaHintBanner()
  }
}
