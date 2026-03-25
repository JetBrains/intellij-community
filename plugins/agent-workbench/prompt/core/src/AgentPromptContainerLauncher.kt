// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface AgentPromptContainerLauncher {
  fun isAvailable(): Boolean
  fun launch(project: Project, request: AgentPromptLaunchRequest)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<AgentPromptContainerLauncher> =
      ExtensionPointName("com.intellij.agent.workbench.containerLauncher")

    fun findInstance(): AgentPromptContainerLauncher? =
      EP_NAME.extensionList.firstOrNull()
  }
}
