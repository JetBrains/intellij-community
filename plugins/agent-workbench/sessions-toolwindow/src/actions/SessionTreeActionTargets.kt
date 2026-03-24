// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForThreadNode
import com.intellij.openapi.project.Project

internal fun createAgentSessionsTreePopupActionContext(
  project: Project,
  nodeId: SessionTreeId,
  node: SessionTreeNode,
  archiveTargets: List<ArchiveThreadTarget>,
): AgentSessionsTreePopupActionContext? {
  val target = resolveSessionActionTarget(nodeId, node) ?: return null
  return AgentSessionsTreePopupActionContext(
    project = project,
    target = target,
    archiveTargets = archiveTargets,
  )
}

internal fun resolveSessionActionTarget(nodeId: SessionTreeId, node: SessionTreeNode): SessionActionTarget? {
  return when (node) {
    is SessionTreeNode.Project -> {
      val projectId = nodeId as? SessionTreeId.Project ?: return null
      SessionActionTarget.Project(
        path = normalizeAgentWorkbenchPath(projectId.path),
        isOpen = node.project.isOpen,
      )
    }

    is SessionTreeNode.Worktree -> {
      if (nodeId !is SessionTreeId.Worktree) return null
      SessionActionTarget.Worktree(
        path = normalizeAgentWorkbenchPath(node.worktree.path),
      )
    }

    is SessionTreeNode.Thread -> {
      when (nodeId) {
        is SessionTreeId.Thread,
        is SessionTreeId.WorktreeThread -> {
          SessionActionTarget.Thread(
            path = normalizeAgentWorkbenchPath(pathForThreadNode(nodeId, node.project.path)),
            provider = node.thread.provider,
            threadId = node.thread.id,
            title = node.thread.title,
            thread = node.thread,
          )
        }

        else -> null
      }
    }

    is SessionTreeNode.SubAgent -> {
      when (nodeId) {
        is SessionTreeId.SubAgent,
        is SessionTreeId.WorktreeSubAgent -> {
          SessionActionTarget.SubAgent(
            path = normalizeAgentWorkbenchPath(pathForThreadNode(nodeId, node.project.path)),
            provider = node.thread.provider,
            parentThreadId = node.thread.id,
            subAgentId = node.subAgent.id,
            title = node.subAgent.name,
            thread = node.thread,
            subAgent = node.subAgent,
          )
        }

        else -> null
      }
    }

    is SessionTreeNode.MoreProjects -> {
      if (nodeId != SessionTreeId.MoreProjects) return null
      SessionActionTarget.MoreProjects(node.hiddenCount)
    }

    is SessionTreeNode.MoreThreads -> {
      val path = pathForMoreThreadsNode(nodeId) ?: return null
      SessionActionTarget.MoreThreads(
        path = normalizeAgentWorkbenchPath(path),
        hiddenCount = node.hiddenCount,
      )
    }

    is SessionTreeNode.Warning,
    is SessionTreeNode.Error,
    is SessionTreeNode.Empty -> null
  }
}
