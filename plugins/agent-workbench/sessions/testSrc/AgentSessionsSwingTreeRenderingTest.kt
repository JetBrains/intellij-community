// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingTreeRenderingTest {
  @Test
  fun errorRowsTakePrecedenceOverWarningsAndEmptyState() {
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          errorMessage = "Failed to load sessions",
          providerWarnings = listOf(
            AgentSessionProviderWarning(
              provider = AgentSessionProvider.CODEX,
              message = "provider warning",
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertEquals(listOf(SessionTreeId.Error(projectPath)), projectNode.childIds)
  }

  @Test
  fun warningRowsSuppressEmptyStateForLoadedProjects() {
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          providerWarnings = listOf(
            AgentSessionProviderWarning(
              provider = AgentSessionProvider.CLAUDE,
              message = "CLI not available",
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertTrue(projectNode.childIds.any { it == SessionTreeId.Warning(projectPath, AgentSessionProvider.CLAUDE) })
    assertFalse(projectNode.childIds.any { it == SessionTreeId.Empty(projectPath) })
  }

  @Test
  fun moreRowsKeepUnknownCountWhenProviderTotalsAreUnknown() {
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          hasUnknownThreadCount = true,
          threads = listOf(
            AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false),
            AgentSessionThread(id = "thread-2", title = "Thread 2", updatedAt = 90, archived = false),
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf(projectPath to 1),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    val moreNodeId = projectNode.childIds.firstOrNull { it == SessionTreeId.MoreThreads(projectPath) }
    val moreThreads = moreNodeId?.let { model.entriesById[it]?.node as? SessionTreeNode.MoreThreads }
    assertEquals(null, moreThreads?.hiddenCount)
  }
}
