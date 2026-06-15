// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class AgentSessionsActivityStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<AgentSessionsSystemNotificationService>()
    project.serviceAsync<AgentSessionsActivityService>()
  }
}
