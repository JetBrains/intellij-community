// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.archiveTargetFromThreadNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.sessionTreeNodeSearchText
import com.intellij.agent.workbench.sessions.toolwindow.ui.SessionTreeStrictSubstringComparator
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsTreeSnapshotTest {
  @Test
  fun modelLimitsClosedProjectsAndAddsMoreNode() {
    val projectPath = "/work/project-a"
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = "Project A",
        isOpen = false,
        providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
        threads = listOf(
          AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false, provider = AgentSessionProvider.CODEX),
          AgentSessionThread(id = "thread-2", title = "Thread 2", updatedAt = 90, archived = false, provider = AgentSessionProvider.CODEX),
        ),
      ),
      AgentProjectSessions(path = "/work/project-b", name = "Project B", isOpen = false, providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
      AgentProjectSessions(path = "/work/project-open", name = "Project Open", isOpen = true, providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
    )

    val model = buildSessionTreeModel(
      projects = projects,
      visibleClosedProjectCount = 1,
      visibleThreadCounts = mapOf(projectPath to 1),
      treeUiState = InMemorySessionTreeUiState(),
    )

    assertThat(model.rootIds)
      .isEqualTo(
        listOf(
          SessionTreeId.Project(projectPath),
          SessionTreeId.Project("/work/project-open"),
          SessionTreeId.MoreProjects,
        )
      )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertThat(projectNode.childIds.any { it == SessionTreeId.MoreThreads(projectPath) }).isTrue()
  }

  @Test
  fun autoOpenProjectsSkipCollapsedProjects() {
    val uiState = InMemorySessionTreeUiState()
    uiState.setProjectCollapsed("/work/project-open", collapsed = true)

    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-open",
          name = "Project Open",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          threads = listOf(AgentSessionThread(id = "thread-1",
                                              title = "Thread 1",
                                              updatedAt = 100,
                                              archived = false,
                                              provider = AgentSessionProvider.CODEX)),
        ),
        AgentProjectSessions(
          path = "/work/project-error",
          name = "Project Error",
          isOpen = false,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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

    assertThat(target.path).isEqualTo("/work/project-a-feature")
    assertThat(target.provider).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(target.threadId).isEqualTo("thread-1")
  }

  @Test
  fun searchTextUsesRenderedThreadTitle() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      id = "codex-1",
      title = "Recheck and fix BazelTargetsOnly.kt",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )

    val searchText = sessionTreeNodeSearchText(SessionTreeNode.Thread(project, thread))

    assertThat(searchText).contains("Recheck and fix BazelTargetsOnly.kt")
    assertThat(searchText).doesNotContain("codex")
  }

  @Test
  fun renderedSearchTextDoesNotCreateProviderLetterMatches() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      id = "thread-1",
      title = "developers",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )

    val searchText = sessionTreeNodeSearchText(SessionTreeNode.Thread(project, thread))

    assertThat(searchText).isEqualTo("developers")
    val comparator = SessionTreeStrictSubstringComparator()
    assertThat(comparator.matchingFragments("desc", searchText)).isNull()
    assertThat(comparator.matchingFragments("dev", searchText)).isNotNull()
    assertThat(comparator.matchingFragments("eve", "developers")).isNull()
    assertThat(comparator.matchingFragments("Desc", "Image Content Description Task")).isNotNull()
    assertThat(comparator.matchingFragments("desc", "encoded scheme")).isNull()
  }
}
