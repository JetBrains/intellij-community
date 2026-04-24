// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.openapi.project.Project

internal fun resolveManualContextAvailability(
  hostProject: Project,
  invocationData: AgentPromptInvocationData,
  launcher: AgentPromptLauncherBridge?,
  sources: List<AgentPromptManualContextSourceBridge>,
): ManualContextAvailability? {
  val sourceProject = when {
    launcher == null -> hostProject
    else -> launcher.resolveSourceProject(invocationData) ?: return null
  }

  return ManualContextAvailability(
    sourceProject = sourceProject,
    sources = sources
      .filter { source -> source.isAvailable(sourceProject) }
      .sortedWith(compareBy(AgentPromptManualContextSourceBridge::order, AgentPromptManualContextSourceBridge::sourceId)),
  )
}
