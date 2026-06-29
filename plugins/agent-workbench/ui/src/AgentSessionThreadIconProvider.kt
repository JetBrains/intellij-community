// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface AgentSessionThreadIconProvider {
  fun getIcon(projectPath: String, provider: AgentSessionProvider, threadId: String): Icon?

  companion object {
    private val EP_NAME = ExtensionPointName<AgentSessionThreadIconProvider>("com.intellij.agent.workbench.sessionThreadIconProvider")

    fun resolve(projectPath: String, provider: AgentSessionProvider, threadId: String): Icon? {
      if (projectPath.isBlank() || threadId.isBlank()) return null
      if (!EP_NAME.hasAnyExtensions()) return null
      return EP_NAME.extensionList.firstNotNullOfOrNull { providerExtension ->
        providerExtension.getIcon(projectPath, provider, threadId)
      }
    }
  }
}

@ApiStatus.Internal
fun resolveAgentSessionThreadIcon(projectPath: String, provider: AgentSessionProvider, threadId: String): Icon? {
  return AgentSessionThreadIconProvider.resolve(projectPath, provider, threadId)
}
