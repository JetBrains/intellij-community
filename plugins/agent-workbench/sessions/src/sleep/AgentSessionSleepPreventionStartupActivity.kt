// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.sleep

// @spec community/plugins/agent-workbench/spec/agent-sessions-sleep-prevention.spec.md

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class AgentSessionSleepPreventionStartupActivity : ProjectActivity {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun execute(project: Project) {
    service<AgentSessionSleepPreventionService>()
  }
}
