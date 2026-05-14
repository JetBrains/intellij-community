// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.config

import com.intellij.agent.workbench.common.extensions.SingleExtensionPointResolver
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

fun interface AgentWorkbenchProjectRuntimeConfigProvider {
  fun isRefreshVfsOnStatusUpdatesEnabled(projectRoot: String): Boolean
}

private class AgentWorkbenchProjectRuntimeConfigProviderRegistry

private val LOG = logger<AgentWorkbenchProjectRuntimeConfigProviderRegistry>()
private val AGENT_WORKBENCH_PROJECT_RUNTIME_CONFIG_PROVIDER_EP: ExtensionPointName<AgentWorkbenchProjectRuntimeConfigProvider> =
  ExtensionPointName("com.intellij.agent.workbench.projectRuntimeConfigProvider")

private val REGISTERED_PROVIDER = SingleExtensionPointResolver(
  log = LOG,
  extensionPoint = AGENT_WORKBENCH_PROJECT_RUNTIME_CONFIG_PROVIDER_EP,
  unavailableMessage = "Agent Workbench project runtime config provider EP is unavailable in this context",
  multipleExtensionsMessage = { providers ->
    "Multiple Agent Workbench project runtime config providers registered; using first: ${providers.map { it::class.java.name }}"
  },
)

object AgentWorkbenchProjectRuntimeConfigs {
  fun isRefreshVfsOnStatusUpdatesEnabled(projectRoot: String): Boolean {
    val provider = REGISTERED_PROVIDER.findFirstOrNull() ?: return true
    return try {
      provider.isRefreshVfsOnStatusUpdatesEnabled(projectRoot)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to resolve Agent Workbench project runtime config for $projectRoot", t)
      true
    }
  }
}
