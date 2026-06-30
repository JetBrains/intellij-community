// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderStatus
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForTaskFolderNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForThreadNode
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.project.Project

internal fun createAgentSessionsTreePopupActionContext(
  project: Project,
  nodeId: SessionTreeId,
  node: SessionTreeNode,
  archiveTargets: List<ArchiveThreadTarget>,
  unarchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  selectedThreadTargets: List<SessionActionTarget.Thread> = emptyList(),
  taskFolderArchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  newThreadActionAvailable: Boolean = true,
): AgentSessionsTreePopupActionContext? {
  val target = resolveSessionActionTarget(nodeId, node)
  val taskFolderTarget = resolveTaskFolderActionTarget(nodeId, node)
  if (target == null && taskFolderTarget == null) return null
  return AgentSessionsTreePopupActionContext(
    project = project,
    target = target,
    taskFolderTarget = taskFolderTarget,
    archiveTargets = archiveTargets,
    unarchiveTargets = unarchiveTargets,
    selectedThreadTargets = selectedThreadTargets,
    taskFolderArchiveTargets = taskFolderArchiveTargets,
    newThreadActionAvailable = newThreadActionAvailable,
  )
}

internal fun createAgentSessionsTreeEmptyAreaPopupActionContext(
  project: Project,
  sourcePath: String?,
  newThreadActionAvailable: Boolean = true,
): AgentSessionsTreePopupActionContext? {
  val normalizedSourcePath = sourcePath
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let(::normalizeAgentWorkbenchPath)
    ?: return null
  return AgentSessionsTreePopupActionContext(
    project = project,
    sourcePath = normalizedSourcePath,
    archiveTargets = emptyList(),
    newThreadActionAvailable = newThreadActionAvailable,
  )
}

internal fun resolveSessionActionTarget(nodeId: SessionTreeId, node: SessionTreeNode): SessionActionTarget? {
  return when (node) {
    is SessionTreeNode.PinnedSection,
    is SessionTreeNode.SectionSeparator,
      -> null

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

    is SessionTreeNode.TaskFolder -> null

    is SessionTreeNode.Thread -> {
      if (isAgentSessionNewSessionId(node.thread.id)) return null
      when (nodeId) {
        is SessionTreeId.Thread,
        is SessionTreeId.WorktreeThread,
          -> {
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
        is SessionTreeId.WorktreeSubAgent,
          -> {
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
    is SessionTreeNode.Empty,
      -> null
  }
}

private fun resolveTaskFolderActionTarget(nodeId: SessionTreeId, node: SessionTreeNode): AgentTaskFolderActionTarget? {
  if (node !is SessionTreeNode.TaskFolder) return null
  val path = pathForTaskFolderNode(nodeId) ?: return null
  return AgentTaskFolderActionTarget(
    path = normalizeAgentWorkbenchPath(path),
    folderId = node.folder.id,
    name = node.folder.name,
    isDone = node.folder.status == AgentTaskFolderStatus.DONE,
    metadata = node.folder.metadata,
  )
}
