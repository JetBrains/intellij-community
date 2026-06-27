// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatOpenTabsPresentationState
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.parentNodesForSelection
import com.intellij.agent.workbench.sessions.toolwindow.ui.sessionTreeExpansionTargetsAfterModelSwap
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),),
        AgentProjectSessions(
          path = "/work/project-error",
          name = "Project Error",
          isOpen = false,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
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
  fun currentProjectScopeHasNoAutoOpenProjectsAfterFlattening() {
    val uiState = InMemorySessionTreeUiState()
    uiState.setProjectCollapsed("/work/project-open", collapsed = true)

    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-open",
          name = "Project Open",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = uiState,
      currentProjectScopeActive = true,
    )

    assertThat(model.rootIds).containsExactly(SessionTreeId.Empty("/work/project-open"))
    assertThat(model.entriesById).doesNotContainKey(SessionTreeId.Project("/work/project-open"))
    assertThat(model.autoOpenProjects).isEmpty()
  }

  @Test
  fun autoOpenProjectsIncludeProjectsWithOpenWorktrees() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = false,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
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
  fun rootMoreProjectsDoesNotAutoExpandPreviouslyVisibleProjectSubtrees() {
    val projectA = AgentProjectSessions(
      path = "/work/project-a",
      name = "Project A",
      isOpen = true,
      providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),)
    val projectB = AgentProjectSessions(
      path = "/work/project-b",
      name = "Project B",
      isOpen = false,
      providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
      errorMessage = "Failed",
    )
    val previousModel = buildSessionTreeModel(
      projects = listOf(projectA, projectB),
      visibleClosedProjectCount = 0,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )
    val nextModel = buildSessionTreeModel(
      projects = listOf(projectA, projectB),
      visibleClosedProjectCount = 1,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )

    val projectAId = SessionTreeId.Project("/work/project-a")
    val projectBId = SessionTreeId.Project("/work/project-b")
    assertThat(
      sessionTreeExpansionTargetsAfterModelSwap(
        model = nextModel,
        previousModel = previousModel,
        rootChanged = true,
        previouslyExpandedTreeIds = emptySet(),
        selectedTreeIds = emptyList(),
      )
    ).containsExactly(projectBId)
    assertThat(
      sessionTreeExpansionTargetsAfterModelSwap(
        model = nextModel,
        previousModel = previousModel,
        rootChanged = true,
        previouslyExpandedTreeIds = setOf(projectAId),
        selectedTreeIds = emptyList(),
      )
    ).containsExactly(projectAId, projectBId)
  }

  @Test
  fun worktreeSubAgentSelectionExpandsProjectWorktreeAndThreadParents() {
    val selectedTreeId = SessionTreeId.WorktreeSubAgent(
      projectPath = "/work/project-a",
      worktreePath = "/work/project-a-feature",
      provider = AgentSessionProvider.from("codex"),
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
          provider = AgentSessionProvider.from("codex"),
          threadId = "thread-1",
        ),
      )
    )
  }

  @Test
  fun pinnedThreadSelectionExpandsPinnedSectionParent() {
    val provider = AgentSessionProvider.from("codex")
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = false,
          providerLoadStates = loadedProviderStates(provider),
          threads = listOf(
            AgentSessionThread(
              id = "thread-a",
              title = "Thread A",
              updatedAt = 100,
              archived = false,
              provider = provider,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
      openTabsPresentationState = AgentChatOpenTabsPresentationState(
        pinnedTopLevelThreadIdsByProvider = mapOf(provider to mapOf(projectPath to setOf("thread-a"))),
      ),
    )
    val selectedTreeId = SessionTreeId.Thread(projectPath, provider, "thread-a")

    assertThat(
      sessionTreeExpansionTargetsAfterModelSwap(
        model = model,
        previousModel = SessionTreeModel.EMPTY,
        rootChanged = true,
        previouslyExpandedTreeIds = emptySet(),
        selectedTreeIds = listOf(selectedTreeId),
      )
    ).containsExactly(SessionTreeId.Pinned)
  }

  @Test
  fun flatPinnedSeparatorDoesNotAutoExpandInCurrentProjectScope() {
    val provider = AgentSessionProvider.from("codex")
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = false,
          providerLoadStates = loadedProviderStates(provider),
          threads = listOf(
            AgentSessionThread(
              id = "thread-a",
              title = "Thread A",
              updatedAt = 100,
              archived = false,
              provider = provider,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
      currentProjectScopeActive = true,
      openTabsPresentationState = AgentChatOpenTabsPresentationState(
        pinnedTopLevelThreadIdsByProvider = mapOf(provider to mapOf(projectPath to setOf("thread-a"))),
      ),
    )
    val selectedTreeId = SessionTreeId.Thread(projectPath, provider, "thread-a")

    assertThat(model.rootIds).containsExactly(SessionTreeId.Pinned, selectedTreeId, SessionTreeId.PinnedSeparator, SessionTreeId.Empty(projectPath))
    assertThat(model.entriesById.getValue(SessionTreeId.Pinned).childIds).isEmpty()
    assertThat(
      sessionTreeExpansionTargetsAfterModelSwap(
        model = model,
        previousModel = SessionTreeModel.EMPTY,
        rootChanged = true,
        previouslyExpandedTreeIds = emptySet(),
        selectedTreeIds = listOf(selectedTreeId),
      )
    ).isEmpty()
  }

  @Test
  fun multipleSelectedRowsExpandAllParents() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = false,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
          threads = listOf(
            AgentSessionThread(
              id = "thread-a",
              title = "Thread A",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.from("codex"),
            )
          ),
        ),
        AgentProjectSessions(
          path = "/work/project-b",
          name = "Project B",
          isOpen = false,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
          worktrees = listOf(
            AgentWorktree(
              path = "/work/project-b-feature",
              name = "project-b-feature",
              branch = "feature",
              isOpen = false,
              threads = listOf(
                AgentSessionThread(
                  id = "thread-b",
                  title = "Thread B",
                  updatedAt = 200,
                  archived = false,
                  provider = AgentSessionProvider.from("codex"),
                  subAgents = listOf(AgentSubAgent(id = "sub-b", name = "Sub B")),
                )
              ),
            )
          ),
        ),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf(
        "/work/project-a" to 10,
        "/work/project-b-feature" to 10,
      ),
      treeUiState = InMemorySessionTreeUiState(),
    )
    val selectedProjectThread = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.from("codex"), "thread-a")
    val selectedWorktreeSubAgent = SessionTreeId.WorktreeSubAgent(
      projectPath = "/work/project-b",
      worktreePath = "/work/project-b-feature",
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-b",
      subAgentId = "sub-b",
    )

    assertThat(
      sessionTreeExpansionTargetsAfterModelSwap(
        model = model,
        previousModel = SessionTreeModel.EMPTY,
        rootChanged = true,
        previouslyExpandedTreeIds = emptySet(),
        selectedTreeIds = listOf(selectedProjectThread, selectedWorktreeSubAgent),
      )
    ).containsExactly(
      SessionTreeId.Project("/work/project-a"),
      SessionTreeId.Project("/work/project-b"),
      SessionTreeId.Worktree("/work/project-b", "/work/project-b-feature"),
      SessionTreeId.WorktreeThread(
        projectPath = "/work/project-b",
        worktreePath = "/work/project-b-feature",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-b",
      ),
    )
  }
}
