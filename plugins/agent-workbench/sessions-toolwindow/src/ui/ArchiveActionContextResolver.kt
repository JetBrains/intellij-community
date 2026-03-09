// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.openapi.project.Project

internal fun resolveArchiveActionContext(
  popupActionContext: AgentSessionsTreePopupActionContext?,
  project: Project,
  selectedTreeId: SessionTreeId?,
  selectedTreeNode: SessionTreeNode?,
  selectedArchiveTargets: List<ArchiveThreadTarget>,
): AgentSessionsTreePopupActionContext? {
  if (popupActionContext != null) {
    return popupActionContext
  }

  if (selectedTreeId == null || selectedTreeNode == null) {
    return null
  }

  return AgentSessionsTreePopupActionContext(
    project = project,
    nodeId = selectedTreeId,
    node = selectedTreeNode,
    archiveTargets = selectedArchiveTargets,
  )
}
