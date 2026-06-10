// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.tree

import com.intellij.agent.workbench.chat.AgentChatOpenPendingTabsState
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId

internal fun overlayPendingAgentChatTabs(
  state: AgentSessionsState,
  pendingTabsState: AgentChatOpenPendingTabsState,
): AgentSessionsState {
  val pendingThreadsByPath = buildPendingThreadsByPath(pendingTabsState)
  if (pendingThreadsByPath.isEmpty()) {
    return state
  }

  var changed = false
  val projects = state.projects.map { project ->
    val projectPendingThreads = pendingThreadsByPath[normalizeAgentWorkbenchPath(project.path)].orEmpty()
    val updatedProject = if (projectPendingThreads.isEmpty()) {
      project
    }
    else {
      changed = true
      project.copy(threads = mergeThreadsWithPendingRows(project.threads, projectPendingThreads))
    }

    val worktrees = updatedProject.worktrees.map { worktree ->
      val worktreePendingThreads = pendingThreadsByPath[normalizeAgentWorkbenchPath(worktree.path)].orEmpty()
      if (worktreePendingThreads.isEmpty()) {
        worktree
      }
      else {
        changed = true
        worktree.copy(threads = mergeThreadsWithPendingRows(worktree.threads, worktreePendingThreads))
      }
    }
    if (worktrees == updatedProject.worktrees) updatedProject else updatedProject.copy(worktrees = worktrees)
  }

  return if (changed) state.copy(projects = projects) else state
}

private fun buildPendingThreadsByPath(
  pendingTabsState: AgentChatOpenPendingTabsState,
): Map<String, List<AgentSessionThread>> {
  val pendingThreadsByPath = LinkedHashMap<String, LinkedHashMap<PendingThreadKey, AgentSessionThread>>()
  for (provider in pendingTabsState.providers()) {
    for ((path, pendingTabs) in pendingTabsState.pendingTabsByPath(provider)) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      val threadsByKey = pendingThreadsByPath.getOrPut(normalizedPath) { LinkedHashMap() }
      for (pendingTab in pendingTabs) {
        val pendingThread = buildPendingThread(provider = provider, pendingTab = pendingTab) ?: continue
        val key = PendingThreadKey(provider = pendingThread.provider, threadId = pendingThread.id)
        val existing = threadsByKey[key]
        if (existing == null || pendingThread.updatedAt > existing.updatedAt) {
          threadsByKey[key] = pendingThread
        }
      }
    }
  }
  return pendingThreadsByPath.mapValues { (_, threadsByKey) -> sortThreadsForTree(threadsByKey.values.toList()) }
}

private fun buildPendingThread(
  provider: AgentSessionProvider,
  pendingTab: AgentChatPendingTabSnapshot,
): AgentSessionThread? {
  val identity = parseAgentThreadIdentity(pendingTab.pendingThreadIdentity) ?: return null
  val identityProvider = AgentSessionProvider.fromOrNull(identity.providerId) ?: return null
  if (identityProvider != provider || !isAgentSessionNewSessionId(identity.threadId)) {
    return null
  }
  return AgentSessionThread(
    id = identity.threadId,
    title = AgentSessionsBundle.message("toolwindow.action.new.thread"),
    updatedAt = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs ?: 0L,
    archived = false,
    activity = AgentThreadActivity.READY,
    provider = provider,
  )
}

private fun mergeThreadsWithPendingRows(
  threads: List<AgentSessionThread>,
  pendingThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  if (pendingThreads.isEmpty()) {
    return threads
  }
  val threadsByKey = LinkedHashMap<PendingThreadKey, AgentSessionThread>(threads.size + pendingThreads.size)
  threads.forEach { thread ->
    threadsByKey[PendingThreadKey(provider = thread.provider, threadId = thread.id)] = thread
  }
  for (pendingThread in pendingThreads) {
    val key = PendingThreadKey(provider = pendingThread.provider, threadId = pendingThread.id)
    val existing = threadsByKey[key]
    if (existing == null || pendingThread.updatedAt >= existing.updatedAt) {
      threadsByKey[key] = pendingThread
    }
  }
  return sortThreadsForTree(threadsByKey.values.toList())
}

private fun sortThreadsForTree(threads: List<AgentSessionThread>): List<AgentSessionThread> {
  return threads.sortedWith(
    compareByDescending<AgentSessionThread> { it.updatedAt }
      .thenBy { thread -> thread.provider.value }
      .thenBy { thread -> thread.id }
  )
}

private data class PendingThreadKey(
  val provider: AgentSessionProvider,
  val threadId: String,
)
