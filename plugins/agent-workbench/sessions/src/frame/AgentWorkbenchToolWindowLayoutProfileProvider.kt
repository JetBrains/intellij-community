// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.agent.workbench.sessions.frame

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.toolWindow.ToolWindowLayoutApplyMode
import com.intellij.toolWindow.ToolWindowLayoutProfileProvider

internal class AgentWorkbenchToolWindowLayoutProfileProvider : ToolWindowLayoutProfileProvider {
  override fun getLayout(project: Project, profileId: String, isNewUi: Boolean): DesktopLayout? {
    if (project.isDisposed) {
      return null
    }
    if (profileId != AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID) {
      return null
    }

    val baseLayout = ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy()
    val infos = baseLayout.getInfos()
      .asSequence()
      .filterNot { (id, _) -> id == ToolWindowId.PROJECT_VIEW || id == ToolWindowId.STRUCTURE_VIEW }
      .associateTo(LinkedHashMap()) { (id, info) -> id to info.copy() }

    val sessionsInfo = infos.get(AGENT_SESSIONS_TOOL_WINDOW_ID) ?: WindowInfoImpl()
    val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    val nextOrderOnLeft = infos.values.asSequence()
      .filter { it.toolWindowPaneId == paneId && it.anchor == ToolWindowAnchor.LEFT && it.order >= 0 }
      .maxOfOrNull { it.order + 1 } ?: 0

    sessionsInfo.id = AGENT_SESSIONS_TOOL_WINDOW_ID
    sessionsInfo.toolWindowPaneId = paneId
    sessionsInfo.anchor = ToolWindowAnchor.LEFT
    sessionsInfo.order = nextOrderOnLeft
    sessionsInfo.isVisible = true
    sessionsInfo.isShowStripeButton = true
    sessionsInfo.weight = 0.25f
    infos.put(AGENT_SESSIONS_TOOL_WINDOW_ID, sessionsInfo)

    val nextOrderOnBottom = infos.values.asSequence()
      .filter { it.toolWindowPaneId == paneId && it.anchor == ToolWindowAnchor.BOTTOM && it.order >= 0 }
      .maxOfOrNull { it.order + 1 } ?: 0
    val terminalInfo = infos.get(TERMINAL_TOOL_WINDOW_ID) ?: WindowInfoImpl()
    val needsBottomPlacement = terminalInfo.toolWindowPaneId != paneId ||
                               terminalInfo.anchor != ToolWindowAnchor.BOTTOM ||
                               terminalInfo.order < 0
    terminalInfo.id = TERMINAL_TOOL_WINDOW_ID
    terminalInfo.toolWindowPaneId = paneId
    terminalInfo.anchor = ToolWindowAnchor.BOTTOM
    if (needsBottomPlacement) {
      terminalInfo.order = nextOrderOnBottom
    }
    terminalInfo.isVisible = false
    terminalInfo.isShowStripeButton = true
    infos.put(TERMINAL_TOOL_WINDOW_ID, terminalInfo)

    return DesktopLayout(infos, baseLayout.unifiedWeights.copy())
  }

  override fun getApplyMode(project: Project, profileId: String, isNewUi: Boolean): ToolWindowLayoutApplyMode {
    return if (profileId == AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID) {
      ToolWindowLayoutApplyMode.FORCE_ONCE
    }
    else {
      ToolWindowLayoutApplyMode.SEED_ONLY
    }
  }

  override fun getMigrationVersion(project: Project, profileId: String, isNewUi: Boolean): Int {
    return if (profileId == AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID) {
      AGENT_WORKBENCH_LAYOUT_MIGRATION_VERSION
    }
    else {
      0
    }
  }
}
