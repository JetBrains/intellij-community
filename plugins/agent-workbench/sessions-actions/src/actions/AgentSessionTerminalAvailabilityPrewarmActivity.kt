// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Prewarms [AgentSessionProviderAvailabilityService] for the project at startup so AWB synchronous menus
 * (`NewThread`, `ResolveConflicts`, etc.) can render the launch-time CLI availability on first
 * paint. If the first UI paint wins the race against startup prewarm, synchronous surfaces show
 * prominent providers optimistically and keep discoverable providers hidden until the background
 * refresh resolves them as available.
 */
internal class AgentSessionTerminalAvailabilityPrewarmActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<AgentSessionProviderAvailabilityService>().refreshNow(force = false)
  }
}
