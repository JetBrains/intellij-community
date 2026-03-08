// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.normalizeArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.SessionWarmState

internal class AgentSessionContentRepository(
  private val stateStore: AgentSessionsStateStore,
  private val warmState: SessionWarmState,
) {
  fun getWarmSnapshot(path: String): AgentSessionWarmPathSnapshot? {
    return warmState.getPathSnapshot(path)
  }

  fun retainWarmSnapshots(paths: Set<String>): Boolean {
    return warmState.retainPathSnapshots(paths)
  }

  fun syncWarmSnapshotFromRuntime(path: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val content = stateStore.snapshot().findPathContent(normalizedPath)
    if (content == null || !content.isOpen) {
      return warmState.removePathSnapshot(normalizedPath)
    }
    if (content.errorMessage != null) {
      return false
    }
    return warmState.setPathSnapshot(
      normalizedPath,
      AgentSessionWarmPathSnapshot(
        threads = content.threads,
        hasUnknownThreadCount = content.hasUnknownThreadCount,
        updatedAt = System.currentTimeMillis(),
      ),
    )
  }

  fun syncWarmSnapshotsFromRuntime(paths: Iterable<String>): Boolean {
    var changed = false
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    for (path in normalizedPaths) {
      changed = syncWarmSnapshotFromRuntime(path) || changed
    }
    return changed
  }

  fun removeArchivedTarget(target: ArchiveThreadTarget): Boolean {
    val normalizedTarget = normalizeArchiveThreadTarget(target)
    var runtimeChanged = false
    stateStore.update { state ->
      var stateChanged = false
      val nextProjects = state.projects.map { project ->
        if (project.path == normalizedTarget.path) {
          val nextThreads = removeArchivedTarget(project.threads, normalizedTarget)
          if (nextThreads != project.threads) {
            stateChanged = true
            runtimeChanged = true
            project.copy(threads = nextThreads)
          }
          else {
            project
          }
        }
        else {
          val nextWorktrees = project.worktrees.map { worktree ->
            if (worktree.path == normalizedTarget.path) {
              val nextThreads = removeArchivedTarget(worktree.threads, normalizedTarget)
              if (nextThreads != worktree.threads) {
                stateChanged = true
                runtimeChanged = true
                worktree.copy(threads = nextThreads)
              }
              else {
                worktree
              }
            }
            else {
              worktree
            }
          }
          if (nextWorktrees == project.worktrees) project else project.copy(worktrees = nextWorktrees)
        }
      }
      if (!stateChanged) state else state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
    }

    val warmChanged = updateWarmSnapshot(normalizedTarget.path) { snapshot ->
      val nextThreads = removeArchivedTarget(snapshot.threads, normalizedTarget)
      if (nextThreads == snapshot.threads) {
        null
      }
      else {
        snapshot.copy(threads = nextThreads, updatedAt = System.currentTimeMillis())
      }
    }
    return runtimeChanged || warmChanged
  }

  fun markThreadAsRead(path: String, provider: AgentSessionProvider, threadId: String, updatedAt: Long): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    var changed = false
    stateStore.update { state ->
      val nextProjects = state.projects.map { project ->
        if (project.path == normalizedPath) {
          val nextThreads = markThreadAsRead(project.threads, provider, threadId, updatedAt)
          if (nextThreads != project.threads) {
            changed = true
            project.copy(threads = nextThreads)
          }
          else {
            project
          }
        }
        else {
          val nextWorktrees = project.worktrees.map { worktree ->
            if (worktree.path == normalizedPath) {
              val nextThreads = markThreadAsRead(worktree.threads, provider, threadId, updatedAt)
              if (nextThreads != worktree.threads) {
                changed = true
                worktree.copy(threads = nextThreads)
              }
              else {
                worktree
              }
            }
            else {
              worktree
            }
          }
          if (nextWorktrees == project.worktrees) project else project.copy(worktrees = nextWorktrees)
        }
      }
      if (!changed) state else state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
    }
    if (changed) {
      syncWarmSnapshotFromRuntime(normalizedPath)
    }
    return changed
  }

  private fun updateWarmSnapshot(
    path: String,
    transform: (AgentSessionWarmPathSnapshot) -> AgentSessionWarmPathSnapshot?,
  ): Boolean {
    val snapshot = warmState.getPathSnapshot(path) ?: return false
    val nextSnapshot = transform(snapshot) ?: return false
    return warmState.setPathSnapshot(path, nextSnapshot)
  }
}

private data class PathContent(
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val hasUnknownThreadCount: Boolean,
  @JvmField val errorMessage: String?,
)

private fun AgentSessionsState.findPathContent(normalizedPath: String): PathContent? {
  projects.firstOrNull { it.path == normalizedPath }
    ?.let { project -> return project.toPathContent() }

  projects.forEach { project ->
    project.worktrees.firstOrNull { it.path == normalizedPath }
      ?.let { worktree -> return worktree.toPathContent() }
  }
  return null
}

private fun AgentProjectSessions.toPathContent(): PathContent {
  return PathContent(
    isOpen = isOpen,
    threads = threads,
    hasUnknownThreadCount = hasUnknownThreadCount,
    errorMessage = errorMessage,
  )
}

private fun AgentWorktree.toPathContent(): PathContent {
  return PathContent(
    isOpen = isOpen,
    threads = threads,
    hasUnknownThreadCount = hasUnknownThreadCount,
    errorMessage = errorMessage,
  )
}

private fun removeArchivedTarget(
  threads: List<AgentSessionThread>,
  target: ArchiveThreadTarget,
): List<AgentSessionThread> {
  return when (target) {
    is ArchiveThreadTarget.Thread -> {
      val nextThreads = threads.filterNot { thread ->
        thread.provider == target.provider && thread.id == target.threadId
      }
      if (nextThreads.size == threads.size) threads else nextThreads
    }

    is ArchiveThreadTarget.SubAgent -> {
      var changed = false
      val nextThreads = threads.map { thread ->
        if (thread.provider != target.provider || thread.id != target.parentThreadId) {
          thread
        }
        else {
          val nextSubAgents = thread.subAgents.filterNot { subAgent -> subAgent.id == target.subAgentId }
          if (nextSubAgents.size != thread.subAgents.size) {
            changed = true
            thread.copy(subAgents = nextSubAgents)
          }
          else {
            thread
          }
        }
      }
      if (changed) nextThreads else threads
    }
  }
}

private fun markThreadAsRead(
  threads: List<AgentSessionThread>,
  provider: AgentSessionProvider,
  threadId: String,
  updatedAt: Long,
): List<AgentSessionThread> {
  var changed = false
  val nextThreads = threads.map { thread ->
    if (thread.provider == provider && thread.id == threadId && thread.activity == AgentThreadActivity.UNREAD && thread.updatedAt <= updatedAt) {
      changed = true
      thread.copy(activity = AgentThreadActivity.READY)
    }
    else {
      thread
    }
  }
  return if (changed) nextThreads else threads
}
