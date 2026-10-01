// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy

/**
 * Keeps scanning and indexing away from a recent project the welcome screen loaded without a frame.
 *
 * Such a project is loaded to run one action on it - a Git branch operation, an update - and is closed again without ever showing a file, so
 * the indexes it would build have no reader. The actions it is loaded for read the repository rather than the indexes.
 */
internal class RecentProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (!RecentProjectsService.getInstance().isFramelessRecentProject(project)) {
      return emptySet()
    }
    return setOf(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
  }

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
}
