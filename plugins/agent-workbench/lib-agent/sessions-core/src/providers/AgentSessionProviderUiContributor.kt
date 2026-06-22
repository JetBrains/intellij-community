// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent

interface AgentSessionProviderUiContributor {
  val provider: AgentSessionProvider

  fun onConversationOpened() {
  }

  fun createToolWindowNorthComponent(project: Project): JComponent? = null
}

object AgentSessionProviderUiContributors {
  val EP_NAME: ExtensionPointName<AgentSessionProviderUiContributor> =
    ExtensionPointName("com.intellij.agent.workbench.sessionProviderUiContributor")

  fun forProvider(provider: AgentSessionProvider): List<AgentSessionProviderUiContributor> {
    return EP_NAME.extensionList.filter { contributor -> contributor.provider == provider }
  }
}
