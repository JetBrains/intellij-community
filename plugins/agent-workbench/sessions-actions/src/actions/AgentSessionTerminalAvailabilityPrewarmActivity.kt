// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Prewarms [AgentSessionProviderAvailabilityService] for the project at startup so AWB synchronous menus
 * (`NewThread`, `ResolveConflicts`, etc.) can render the launch-time CLI availability on first
 * paint. If the first UI paint wins the race against startup prewarm, synchronous surfaces render
 * providers optimistically and update after the background refresh publishes the resolved state.
 */
internal class AgentSessionTerminalAvailabilityPrewarmActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    AgentSessionProviderAvailabilityService.getInstance(project).refreshNow()
  }
}
