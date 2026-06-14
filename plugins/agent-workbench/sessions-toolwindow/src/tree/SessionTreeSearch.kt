// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.tree

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle

internal fun sessionTreeNodeSearchText(node: SessionTreeNode): String {
  return when (node) {
    is SessionTreeNode.Project -> searchText(
      node.project.name,
      visibleProjectBranch(node.project),
      node.pathQualifier,
    )

    is SessionTreeNode.Worktree -> searchText(
      node.worktree.name,
      node.worktree.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached"),
    )

    is SessionTreeNode.Thread -> searchText(
      threadDisplayTitle(threadId = node.thread.id, title = node.thread.title),
      branchMismatchText(node),
    )

    is SessionTreeNode.SubAgent -> node.subAgent.name.ifBlank { node.subAgent.id }
    is SessionTreeNode.Warning -> node.message
    is SessionTreeNode.Error -> node.message
    is SessionTreeNode.Empty -> node.message
    is SessionTreeNode.MoreProjects -> AgentSessionsBundle.message("toolwindow.action.more.count", node.hiddenCount)
    is SessionTreeNode.MoreThreads -> node.hiddenCount?.let { AgentSessionsBundle.message("toolwindow.action.more.count", it) }
                                      ?: AgentSessionsBundle.message("toolwindow.action.more")
  }
}

private fun branchMismatchText(node: SessionTreeNode.Thread): String? {
  val originBranch = node.thread.originBranch
  val parentBranch = node.parentWorktreeBranch
  return if (originBranch != null && parentBranch != null && originBranch != parentBranch) {
    AgentSessionsBundle.message("toolwindow.thread.branch.mismatch", originBranch)
  }
  else {
    null
  }
}

private fun searchText(vararg values: String?): String {
  return values
    .asSequence()
    .filterNot { it.isNullOrBlank() }
    .distinct()
    .joinToString(" ")
}
