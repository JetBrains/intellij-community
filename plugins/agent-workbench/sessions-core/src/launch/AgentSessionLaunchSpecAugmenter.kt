// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.SingleExtensionPointResolver
import com.intellij.agent.workbench.sessions.core.SuspendingOverridableValue
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

fun interface AgentSessionLaunchSpecAugmenter {
  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec
}

private class AgentSessionLaunchSpecAugmenterRegistry

private val LOG = logger<AgentSessionLaunchSpecAugmenterRegistry>()
private val AGENT_SESSION_LAUNCH_SPEC_AUGMENTER_EP: ExtensionPointName<AgentSessionLaunchSpecAugmenter> =
  ExtensionPointName("com.intellij.agent.workbench.sessionLaunchSpecAugmenter")

private val REGISTERED_AUGMENTER = SingleExtensionPointResolver(
  log = LOG,
  extensionPoint = AGENT_SESSION_LAUNCH_SPEC_AUGMENTER_EP,
  unavailableMessage = "Session launch spec augmenter EP is unavailable in this context",
  multipleExtensionsMessage = { augmenters ->
    "Multiple session launch spec augmenters registered; using first: ${augmenters.map { it::class.java.name }}"
  },
)

object AgentSessionLaunchSpecAugmenters {
  private val augmenterOverride = SuspendingOverridableValue<AgentSessionLaunchSpecAugmenter?> { REGISTERED_AUGMENTER.findFirstOrNull() }

  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val augmenter = augmenterOverride.value() ?: return launchSpec
    return augmenter.augment(projectPath = projectPath, provider = provider, launchSpec = launchSpec)
  }

  suspend fun <T> withAugmenterForTest(augmenter: AgentSessionLaunchSpecAugmenter?, action: suspend () -> T): T {
    return augmenterOverride.withOverride(augmenter, action)
  }
}
