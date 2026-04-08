// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import java.awt.Component

data class AgentPromptManualContextPickerRequest(
  @JvmField val hostProject: Project,
  @JvmField val sourceProject: Project,
  @JvmField val invocationData: AgentPromptInvocationData,
  @JvmField val workingProjectPath: String?,
  @JvmField val currentItems: List<AgentPromptContextItem> = emptyList(),
  @JvmField val anchorComponent: Component,
  @JvmField val onSelected: (AgentPromptContextItem) -> Unit,
  @JvmField val onError: (@Nls String) -> Unit,
) {
  val currentItem: AgentPromptContextItem?
    get() = currentItems.firstOrNull()
}

enum class AgentPromptManualContextSelectionMode {
  REPLACE,
  APPEND,
}

interface AgentPromptManualContextSourceBridge {
  val sourceId: String

  val order: Int
    get() = Int.MAX_VALUE

  val selectionMode: AgentPromptManualContextSelectionMode
    get() = AgentPromptManualContextSelectionMode.REPLACE

  fun isAvailable(project: Project): Boolean = true

  fun getDisplayName(): @Nls String

  fun showPicker(request: AgentPromptManualContextPickerRequest)
}

object AgentPromptManualContextSources {
  private val EP_NAME: ExtensionPointName<AgentPromptManualContextSourceBridge> =
    ExtensionPointName("com.intellij.agent.workbench.promptManualContextSource")

  fun allSources(): List<AgentPromptManualContextSourceBridge> = EP_NAME.extensionList
}
