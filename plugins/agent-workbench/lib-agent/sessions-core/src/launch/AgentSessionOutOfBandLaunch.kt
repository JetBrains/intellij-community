// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

data class AgentSessionOutOfBandLaunchContext(
  val provider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
)

/**
 * Delivers a new session's launch out of band — without a managed terminal. Used by non-terminal
 * runtimes (e.g. ACP): the threadView tab renders custom content, and the chosen agent + initial prompt are
 * applied by the implementation instead of being dispatched into a terminal.
 *
 * Looked up by resolved launch context; when one handles the context, the launch service skips
 * terminal dispatch and invokes [launch] once the threadView tab has opened (so a real [Project] is available).
 */
interface AgentSessionOutOfBandLaunch {
  fun handles(provider: AgentSessionProvider): Boolean

  fun handles(context: AgentSessionOutOfBandLaunchContext): Boolean = handles(context.provider)

  /**
   * @param threadId the concrete session id the threadView tab opened with (the provider's preallocated id)
   * @param context the resolved launch context, including the optional launch target selected by the profile
   * @param prompt the initial prompt text, or `null`/blank when none
   */
  suspend fun launch(project: Project, path: String, threadId: String, context: AgentSessionOutOfBandLaunchContext, prompt: String?)

  companion object {
    private val EP = ExtensionPointName<AgentSessionOutOfBandLaunch>("com.intellij.agent.workbench.sessionOutOfBandLaunch")

    @Suppress("unused")
    fun forProvider(provider: AgentSessionProvider): AgentSessionOutOfBandLaunch? =
      if (EP.hasAnyExtensions()) EP.extensionList.firstOrNull { it.handles(provider) } else null

    fun forContext(context: AgentSessionOutOfBandLaunchContext): AgentSessionOutOfBandLaunch? =
      if (EP.hasAnyExtensions()) EP.extensionList.firstOrNull { it.handles(context) } else null
  }
}
