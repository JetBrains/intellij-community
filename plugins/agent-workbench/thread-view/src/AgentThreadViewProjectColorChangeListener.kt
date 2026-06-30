// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsListener
import com.intellij.ide.ProjectColorChangeListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

internal class AgentThreadViewProjectColorChangeListener : ProjectColorChangeListener {
  override fun projectColorChanged(projectPath: String) {
    refreshOpenAgentThreadViewTabColors(sourceProjectPaths = setOf(projectPath))
  }
}

internal class AgentThreadViewSettingsChangeListener : AgentWorkbenchSettingsListener {
  override fun colorTabsBySourceProjectChanged() {
    refreshOpenAgentThreadViewTabColors()
  }
}

internal fun refreshOpenAgentThreadViewTabColors(
  projects: Array<Project> = ProjectManager.getInstance().openProjects,
  sourceProjectPaths: Set<String>? = null,
  isDedicatedProject: (Project) -> Boolean = ::isAgentWorkbenchDedicatedFrameProject,
  updateFilePresentation: (FileEditorManagerEx, AgentThreadViewVirtualFile) -> Unit = { manager, file -> manager.updateFilePresentation(file) },
): Int {
  var updatedPresentations = 0
  val normalizedSourceProjectPaths = sourceProjectPaths?.mapTo(LinkedHashSet(), ::normalizeAgentWorkbenchPath)
  val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot(projects)
  for (threadViewFile in openTabsSnapshot.files()) {
    if (normalizedSourceProjectPaths != null && normalizeAgentWorkbenchPath(threadViewFile.projectPath) !in normalizedSourceProjectPaths) {
      continue
    }
    for (manager in openTabsSnapshot.managersFor(threadViewFile)) {
      if (!isDedicatedProject(manager.project)) {
        continue
      }
      updateFilePresentation(manager, threadViewFile)
      updatedPresentations++
    }
  }
  return updatedPresentations
}
