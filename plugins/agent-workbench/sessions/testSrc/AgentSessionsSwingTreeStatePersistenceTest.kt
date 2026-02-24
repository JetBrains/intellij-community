// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingTreeStatePersistenceTest {
  @Test
  fun autoOpenProjectsSkipPersistedCollapsedState() {
    val uiState = InMemorySessionsTreeUiState()
    uiState.setProjectCollapsed("/work/project-open", collapsed = true)

    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-open",
          name = "Project Open",
          isOpen = true,
          hasLoaded = true,
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
  fun autoOpenProjectsIncludeProjectsWithOpenWorktrees() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = false,
          hasLoaded = true,
          worktrees = listOf(
            AgentWorktree(
              path = "/work/project-a-feature",
              name = "project-a-feature",
              branch = "feature",
              isOpen = true,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    assertTrue(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-a")))
  }

  @Test
  fun worktreeSubAgentSelectionExpandsProjectWorktreeAndThreadParents() {
    val selectedTreeId = SessionTreeId.WorktreeSubAgent(
      projectPath = "/work/project-a",
      worktreePath = "/work/project-a-feature",
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-1",
      subAgentId = "sub-1",
    )

    assertEquals(
      listOf(
        SessionTreeId.Project("/work/project-a"),
        SessionTreeId.Worktree("/work/project-a", "/work/project-a-feature"),
        SessionTreeId.WorktreeThread(
          projectPath = "/work/project-a",
          worktreePath = "/work/project-a-feature",
          provider = AgentSessionProvider.CODEX,
          threadId = "thread-1",
        ),
      ),
      parentNodesForSelection(selectedTreeId),
    )
  }
}
