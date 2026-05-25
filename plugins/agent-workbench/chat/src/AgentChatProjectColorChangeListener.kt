// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsListener
import com.intellij.ide.ProjectColorChangeListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

internal class AgentChatProjectColorChangeListener : ProjectColorChangeListener {
  override fun projectColorChanged(projectPath: String) {
    refreshOpenAgentChatTabColors(sourceProjectPaths = setOf(projectPath))
  }
}

internal class AgentChatSettingsChangeListener : AgentWorkbenchSettingsListener {
  override fun colorTabsBySourceProjectChanged() {
    refreshOpenAgentChatTabColors()
  }
}

internal fun refreshOpenAgentChatTabColors(
  projects: Array<Project> = ProjectManager.getInstance().openProjects,
  sourceProjectPaths: Set<String>? = null,
  isDedicatedProject: (Project) -> Boolean = ::isAgentWorkbenchDedicatedFrameProject,
  updateFilePresentation: (FileEditorManagerEx, AgentChatVirtualFile) -> Unit = { manager, file -> manager.updateFilePresentation(file) },
): Int {
  var updatedPresentations = 0
  val normalizedSourceProjectPaths = sourceProjectPaths?.mapTo(LinkedHashSet(), ::normalizeAgentWorkbenchPath)
  val openTabsSnapshot = collectOpenAgentChatTabsSnapshot(projects)
  for (chatFile in openTabsSnapshot.files()) {
    if (normalizedSourceProjectPaths != null && normalizeAgentWorkbenchPath(chatFile.projectPath) !in normalizedSourceProjectPaths) {
      continue
    }
    for (manager in openTabsSnapshot.managersFor(chatFile)) {
      if (!isDedicatedProject(manager.project)) {
        continue
      }
      updateFilePresentation(manager, chatFile)
      updatedPresentations++
    }
  }
  return updatedPresentations
}
