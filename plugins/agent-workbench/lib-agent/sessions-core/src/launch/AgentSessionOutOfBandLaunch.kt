// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Delivers a new session's launch out of band — without a managed terminal. Used by non-terminal
 * runtimes (e.g. ACP): the chat tab renders custom content, and the chosen agent + initial prompt are
 * applied by the implementation instead of being dispatched into a terminal.
 *
 * Looked up by [AgentSessionProvider]; when one handles a provider, the launch service skips terminal
 * dispatch and invokes [launch] once the chat tab has opened (so a real [Project] is available).
 */
interface AgentSessionOutOfBandLaunch {
  fun handles(provider: AgentSessionProvider): Boolean

  /**
   * @param threadId the concrete session id the chat tab opened with (the provider's preallocated id)
   * @param modelId the selected generation "model" (for ACP, the chosen agent), or `null`
   * @param prompt the initial prompt text, or `null`/blank when none
   */
  suspend fun launch(project: Project, path: String, threadId: String, modelId: String?, prompt: String?)

  companion object {
    private val EP = ExtensionPointName<AgentSessionOutOfBandLaunch>("com.intellij.agent.workbench.sessionOutOfBandLaunch")

    fun forProvider(provider: AgentSessionProvider): AgentSessionOutOfBandLaunch? =
      if (EP.hasAnyExtensions()) EP.extensionList.firstOrNull { it.handles(provider) } else null
  }
}
