// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal object AgentWorkbenchDedicatedFrameProjectManager {
  private val projectPath: Path by lazy {
    PathManager.getConfigDir().resolve("agent-workbench-chat-frame")
  }

  fun dedicatedProjectPath(): String {
    return projectPath.invariantSeparatorsPathString
  }

  suspend fun ensureProjectPath(): Path {
    val path = projectPath
    withContext(Dispatchers.IO) {
      Files.createDirectories(path)
    }
    coroutineScope {
      launch {
        TrustedProjects.setProjectTrusted(path, true)
      }
      launch {
        serviceAsync<WindowsDefenderChecker>().markProjectPath(path, /*skip =*/ true)
      }
    }
    return path
  }

  fun isDedicatedProjectPath(path: String): Boolean {
    return normalizeAgentWorkbenchPath(path) == dedicatedProjectPath()
  }

  fun isDedicatedProject(project: Project): Boolean {
    val projectPath =
      (RecentProjectsManager.getInstance() as? RecentProjectsManagerBase)?.getProjectPath(project)?.invariantSeparatorsPathString
      ?: project.basePath?.let { normalizeAgentWorkbenchPath(it) }
      ?: return false
    return isDedicatedProjectPath(projectPath)
  }

  suspend fun configureProject(project: Project) {
    val recentProjectsManager = serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase
    recentProjectsManager.setProjectHidden(project, true)
    recentProjectsManager.updateRecentMetadata(project) {
      projectFrameTypeId = AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
    }
    TrustedProjects.setProjectTrusted(project, true)
    TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(project, true)
  }
}
