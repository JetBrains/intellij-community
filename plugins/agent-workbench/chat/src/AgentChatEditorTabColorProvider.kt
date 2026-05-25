// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.ide.RecentProjectColorPalette
import com.intellij.ide.RecentProjectColorInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import kotlin.io.path.invariantSeparatorsPathString

internal class AgentChatEditorTabColorProvider @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = ::isAgentWorkbenchDedicatedFrameProject,
  private val isProjectColorsEnabled: () -> Boolean = { UISettings.getInstance().differentiateProjects },
  private val isAgentChatTabColoringEnabled: () -> Boolean = { AgentWorkbenchSettings.getInstance().colorTabsBySourceProject },
  private val sourceProjectColorInfo: (String) -> RecentProjectColorInfo? = ::sourceProjectColorInfo,
) : EditorTabColorProvider, DumbAware {
  override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
    val chatFile = file as? AgentChatVirtualFile ?: return null
    if (!isDedicatedProject(project) || !isProjectColorsEnabled() || !isAgentChatTabColoringEnabled()) {
      return null
    }

    val sourceProjectPath = normalizeAgentWorkbenchPathOrNull(chatFile.projectPath)?.takeIf { it.isNotBlank() } ?: return null
    val colorInfo = sourceProjectColorInfo(sourceProjectPath) ?: return null
    return RecentProjectColorPalette.softBackground(colorInfo)
  }
}

private const val AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID: String = "AGENT_DEDICATED"

internal fun isAgentWorkbenchDedicatedFrameProject(project: Project): Boolean {
  val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
  val projectPath = recentProjectsManager.getProjectPath(project)?.invariantSeparatorsPathString
                    ?: project.basePath?.let(::normalizeAgentWorkbenchPath)
                    ?: return false
  return recentProjectsManager.getProjectMetaInfo(projectPath)?.projectFrameTypeId == AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
}

private fun sourceProjectColorInfo(sourceProjectPath: String): RecentProjectColorInfo? {
  // Agent Chat tabs may point to closed source projects. Read only already stored recent-project color metadata here:
  // path-based platform color helpers can generate and persist a missing color index for a project path.
  val path = parseAgentWorkbenchPathOrNull(sourceProjectPath) ?: return null
  return RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(path)?.colorInfo
}
