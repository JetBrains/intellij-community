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
class AgentSessionsTreeSnapshotTest {
  @Test
  fun modelLimitsClosedProjectsAndAddsMoreNode() {
    val projectPath = "/work/project-a"
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = "Project A",
        isOpen = false,
        hasLoaded = true,
        threads = listOf(
          AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false),
          AgentSessionThread(id = "thread-2", title = "Thread 2", updatedAt = 90, archived = false),
        ),
      ),
      AgentProjectSessions(path = "/work/project-b", name = "Project B", isOpen = false, hasLoaded = true),
      AgentProjectSessions(path = "/work/project-open", name = "Project Open", isOpen = true, hasLoaded = true),
    )

    val model = buildSessionTreeModel(
      projects = projects,
      visibleClosedProjectCount = 1,
      visibleThreadCounts = mapOf(projectPath to 1),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    assertEquals(
      listOf(
        SessionTreeId.Project(projectPath),
        SessionTreeId.Project("/work/project-open"),
        SessionTreeId.MoreProjects,
      ),
      model.rootIds,
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertTrue(projectNode.childIds.any { it == SessionTreeId.MoreThreads(projectPath) })
  }

  @Test
  fun autoOpenProjectsSkipCollapsedProjects() {
    val uiState = InMemorySessionsTreeUiState()
    uiState.setProjectCollapsed("/work/project-open", collapsed = true)

    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-open",
          name = "Project Open",
          isOpen = true,
          hasLoaded = true,
          threads = listOf(AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false)),
        ),
        AgentProjectSessions(
          path = "/work/project-error",
          name = "Project Error",
          isOpen = false,
          hasLoaded = true,
          errorMessage = "Failed",
        ),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = uiState,
    )

    assertFalse(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-open")))
    assertTrue(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-error")))
  }

  @Test
  fun archiveTargetUsesWorktreePathForWorktreeThread() {
    val thread = SessionTreeNode.Thread(
      project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true),
      thread = AgentSessionThread(
        id = "thread-1",
        title = "Thread 1",
        updatedAt = 10,
        archived = false,
        provider = AgentSessionProvider.CLAUDE,
      ),
    )

    val target = archiveTargetFromThreadNode(
      id = SessionTreeId.WorktreeThread(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-a-feature",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
      ),
      threadNode = thread,
    )

    assertEquals("/work/project-a-feature", target.path)
    assertEquals("thread-1", target.thread.id)
  }
}
