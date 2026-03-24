// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.parentNodesForSelection
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingTreeStatePersistenceTest {
  @Test
  fun autoOpenProjectsSkipPersistedCollapsedState() {
    val uiState = InMemorySessionTreeUiState()
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

    assertThat(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-open"))).isFalse()
    assertThat(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-error"))).isTrue()
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
      treeUiState = InMemorySessionTreeUiState(),
    )

    assertThat(model.autoOpenProjects.contains(SessionTreeId.Project("/work/project-a"))).isTrue()
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

    assertThat(parentNodesForSelection(selectedTreeId)).isEqualTo(
      listOf(
        SessionTreeId.Project("/work/project-a"),
        SessionTreeId.Worktree("/work/project-a", "/work/project-a-feature"),
        SessionTreeId.WorktreeThread(
          projectPath = "/work/project-a",
          worktreePath = "/work/project-a-feature",
          provider = AgentSessionProvider.CODEX,
          threadId = "thread-1",
        ),
      )
    )
  }
}
