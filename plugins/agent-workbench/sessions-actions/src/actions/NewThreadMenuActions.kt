// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.platform.ai.agent.sessions.core.providers.generationSettingsForPlanMode
import com.intellij.platform.ai.agent.sessions.core.providers.initialMessageRequestForLaunchProfile
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return
  service<AgentSessionLaunchService>().createNewSession(
    path = path,
    provider = provider,
    mode = profile.launchMode,
    entryPoint = entryPoint,
    currentProject = currentProject,
    initialMessageRequest = initialMessageRequestForLaunchProfile(profile),
    generationSettings = generationSettingsForPlanMode(
      generationSettings = profile.generationSettings,
      startInPlanMode = false,
    ),
  )
}
