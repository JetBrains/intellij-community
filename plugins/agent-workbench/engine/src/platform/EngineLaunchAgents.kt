// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** A launchable agent variant surfaced by the Engine provider as a generation "model". */
data class EngineLaunchAgent(val id: String, val displayName: String)

/**
 * Contributes launchable Engine agents (e.g. ACP agents from the catalog) to the community Engine
 * provider's model list, so it can offer ultimate-sourced agents in the New Task launch profiles
 * without a compile-time dependency on them.
 */
interface EngineLaunchAgentProvider {
  fun availableAgents(project: Project): List<EngineLaunchAgent>

  companion object {
    private val EP = ExtensionPointName<EngineLaunchAgentProvider>("com.intellij.agent.workbench.engine.launchAgentProvider")

    fun availableAgents(project: Project): List<EngineLaunchAgent> =
      if (EP.hasAnyExtensions()) EP.extensionList.flatMap { it.availableAgents(project) } else emptyList()
  }
}
