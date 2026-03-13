// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context.uiPicker

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.AgentPromptScreenshotContextItem.buildScreenshotContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSourceBridge
import com.intellij.idea.AppMode
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.image.BufferedImage

private const val UI_CONTEXT_SOURCE_ID = "manual.ui.context"
private const val UI_CONTEXT_SOURCE = "manualUiPicker"

internal class AgentPromptUIContextManualContextSource : AgentPromptManualContextSourceBridge {
  override val sourceId: String
    get() = UI_CONTEXT_SOURCE_ID

  override val order: Int
    get() = 30

  override fun isAvailable(project: Project): Boolean {
    return !AppMode.isRemoteDevHost()
  }

  override fun getDisplayName(): String {
    return AgentPromptBundle.message("manual.context.ui.display.name")
  }

  override fun showPicker(request: AgentPromptManualContextPickerRequest) {
    val session = UIContextPickerSession(
      project = request.sourceProject,
      onPicked = { component, screenshot ->
        request.onSelected(buildUIContextItem(component, screenshot))
      },
      onCanceled = {},
    )
    session.start()
  }
}

private fun buildUIContextItem(component: Component, screenshot: BufferedImage): AgentPromptContextItem {
  return buildScreenshotContextItem(
    title = resolveComponentDisplayName(component),
    screenshot = screenshot,
    sourceId = UI_CONTEXT_SOURCE_ID,
    source = UI_CONTEXT_SOURCE,
    tempFilePrefix = "ui-screenshot-",
  )
}

internal val componentNameVocabulary = mapOf(
  "StructureViewComponent" to "Structure",
  "LocalChangesListView" to "Local Changes",
  "Changes" to "Changes",
  "VcsLog" to "VCS Log",
  "problems" to "Problems",
  "projectView" to "Project View",
  "navbar" to "Navigation Bar",
  "console" to "Console Output",
  "build" to "Build Output",
  "terminal" to "Terminal",
  "editor" to "Editor",
)

internal fun resolveComponentDisplayName(component: Component): String {
  var current: Component? = component
  while (current != null) {
    val simpleName = current::class.simpleName?.lowercase() ?: ""
    val key = componentNameVocabulary.keys.firstOrNull { simpleName.contains(it.lowercase()) }
    if (key != null) {
      return componentNameVocabulary[key]!!
    }
    current = current.parent
  }
  return AgentPromptBundle.message("manual.context.ui.default.title")
}
