// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.terminal.frontend.action.TerminalAgentsAvailabilityService

/**
 * Prewarms [TerminalAgentsAvailabilityService] for the project at startup so AWB synchronous menus
 * (`NewThread`, `ResolveConflicts`, etc.) can render the launch-time CLI availability on first
 * paint. Without this prewarm, the cache stays empty until the terminal tool window initializes —
 * the agent prompt popup, the sessions tree, and editor-tab actions would all show every provider
 * as "CLI not found" until the user happens to open the terminal.
 */
internal class AgentSessionTerminalAvailabilityPrewarmActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    TerminalAgentsAvailabilityService.getInstance(project).refreshAvailableAgents()
  }
}
