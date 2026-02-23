// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

interface AgentThreadQuickStartService {
  fun isVisible(project: Project): Boolean

  fun isEnabled(project: Project): Boolean

  fun startNewThread(path: String, project: Project)

  companion object {
    fun getInstance(): AgentThreadQuickStartService? {
      return ApplicationManager.getApplication().getService(AgentThreadQuickStartService::class.java)
    }
  }
}
