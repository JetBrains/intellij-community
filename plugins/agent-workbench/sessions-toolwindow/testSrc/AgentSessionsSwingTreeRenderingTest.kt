// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.sessionTreeNodeSearchText
import com.intellij.agent.workbench.sessions.toolwindow.ui.buildArchivedDisplayState
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.ZoneId

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
      treeUiState = InMemorySessionTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertThat(projectNode.childIds).isEqualTo(listOf(SessionTreeId.Error(projectPath)))
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
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
      treeUiState = InMemorySessionTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    assertThat(projectNode.childIds.any { it == SessionTreeId.Warning(projectPath, AgentSessionProvider.CLAUDE) }).isTrue()
    assertThat(projectNode.childIds.any { it == SessionTreeId.Empty(projectPath) }).isFalse()
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
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          providersWithUnknownThreadCount = setOf(AgentSessionProvider.CODEX),
          threads = listOf(
            AgentSessionThread(id = "thread-1",
                               title = "Thread 1",
                               updatedAt = 100,
                               archived = false,
                               provider = AgentSessionProvider.CODEX),
            AgentSessionThread(id = "thread-2",
                               title = "Thread 2",
                               updatedAt = 90,
                               archived = false,
                               provider = AgentSessionProvider.CODEX),
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf(projectPath to 1),
      treeUiState = InMemorySessionTreeUiState(),
    )

    val projectNode = requireNotNull(model.entriesById[SessionTreeId.Project(projectPath)])
    val moreNodeId = projectNode.childIds.firstOrNull { it == SessionTreeId.MoreThreads(projectPath) }
    val moreThreads = moreNodeId?.let { model.entriesById[it]?.node as? SessionTreeNode.MoreThreads }
    assertThat(moreThreads?.hiddenCount).isNull()
  }

  @Test
  fun duplicateProjectNamesUseProjectNamesAndUniquePathQualifiers() {
    val firstPath = "/work/project-a"
    val secondPath = "/tmp/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(path = firstPath,
                             name = "Project A",
                             isOpen = true,
                             providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
        AgentProjectSessions(path = secondPath,
                             name = "Project A",
                             isOpen = true,
                             providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )

    val firstNode = model.entriesById.getValue(SessionTreeId.Project(firstPath)).node as SessionTreeNode.Project
    val secondNode = model.entriesById.getValue(SessionTreeId.Project(secondPath)).node as SessionTreeNode.Project

    assertThat(firstNode.project.name).isEqualTo("Project A")
    assertThat(secondNode.project.name).isEqualTo("Project A")
    assertThat(firstNode.pathQualifier).isEqualTo("…/work/project-a")
    assertThat(secondNode.pathQualifier).isEqualTo("…/tmp/project-a")
    assertThat(sessionTreeNodeSearchText(firstNode)).isEqualTo("Project A …/work/project-a")
  }

  @Test
  fun differentVisibleBranchesDoNotUsePathQualifiers() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a",
                             name = "Project A",
                             branch = "feature-a",
                             isOpen = true,
                             providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
        AgentProjectSessions(path = "/tmp/project-a",
                             name = "Project A",
                             branch = "feature-b",
                             isOpen = true,
                             providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )

    val firstNode = model.entriesById.getValue(SessionTreeId.Project("/work/project-a")).node as SessionTreeNode.Project
    val secondNode = model.entriesById.getValue(SessionTreeId.Project("/tmp/project-a")).node as SessionTreeNode.Project

    assertThat(firstNode.pathQualifier).isNull()
    assertThat(secondNode.pathQualifier).isNull()
  }

  @Test
  fun archivedDisplayStateFiltersRangeAndDropsEmptyProjects() {
    val zoneId = ZoneId.of("UTC")
    val nowMs = Instant.parse("2026-05-13T12:00:00Z").toEpochMilli()
    val projectPath = "/work/project-a"
    val displayState = buildArchivedDisplayState(
      archivedState = AgentArchivedSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath,
            name = "Project A",
            isOpen = true,
            threads = listOf(
              archivedThread(id = "today", updatedAt = Instant.parse("2026-05-13T09:00:00Z").toEpochMilli()),
              archivedThread(id = "yesterday", updatedAt = Instant.parse("2026-05-12T23:00:00Z").toEpochMilli()),
            ),
            worktrees = listOf(
              AgentWorktree(
                path = "/work/project-a-feature",
                name = "project-a-feature",
                branch = "feature",
                isOpen = false,
                threads = listOf(archivedThread(id = "worktree-old", updatedAt = Instant.parse("2026-05-11T09:00:00Z").toEpochMilli())),
              )
            ),
          ),
          AgentProjectSessions(
            path = "/work/project-old",
            name = "Old Project",
            isOpen = false,
            threads = listOf(archivedThread(id = "old", updatedAt = Instant.parse("2026-04-10T09:00:00Z").toEpochMilli())),
          ),
          AgentProjectSessions(
            path = "/work/project-loading",
            name = "Loading Project",
            isOpen = false,
            providerLoadStates = loadingProviderStates(AgentSessionProvider.CODEX),
          ),
        ),
        lastUpdatedAt = nowMs,
        visibleClosedProjectCount = 7,
        visibleThreadCounts = mapOf(projectPath to 12),
      ),
      rangePreset = AgentSessionArchivedRangePreset.TODAY,
      nowMs = nowMs,
      zoneId = zoneId,
    )

    assertThat(displayState.projects.map { it.path }).containsExactly(projectPath, "/work/project-loading")
    assertThat(displayState.projects.first().threads.map { it.id }).containsExactly("today")
    assertThat(displayState.projects.first().worktrees).isEmpty()
    assertThat(displayState.visibleClosedProjectCount).isEqualTo(7)
    assertThat(displayState.visibleThreadCounts).containsEntry(projectPath, 12)
  }
}

private fun archivedThread(id: String, updatedAt: Long): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = id,
    updatedAt = updatedAt,
    archived = true,
    provider = AgentSessionProvider.CODEX,
  )
}
