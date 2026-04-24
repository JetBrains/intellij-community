// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import java.util.EnumSet

internal class AgentWorkbenchProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      return AGENT_WORKBENCH_FRAME_CAPABILITIES
    }
    else {
      return emptySet()
    }
  }

  /**
   * Applies the startup policy only when aggregated capabilities already classify this frame as
   * VCS-suppressed. The dedicated-project check stays as a defensive guard.
   */
  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
    if (!capabilities.contains(ProjectFrameCapability.SUPPRESS_VCS_UI)) {
      return null
    }

    if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      return null
    }
    return AGENT_WORKBENCH_FRAME_UI_POLICY
  }
}

private val AGENT_WORKBENCH_FRAME_CAPABILITIES = EnumSet.of(
  ProjectFrameCapability.SUPPRESS_VCS_UI,
  ProjectFrameCapability.SUPPRESS_PROJECT_VIEW,
  ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES,
  ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES,
  ProjectFrameCapability.EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER,
)

private val AGENT_WORKBENCH_FRAME_UI_POLICY = ProjectFrameUiPolicy(
  startupToolWindowIdToActivate = AGENT_SESSIONS_TOOL_WINDOW_ID,
  toolWindowLayoutProfileId = AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID,
)
