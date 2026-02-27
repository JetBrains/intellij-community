// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsTreeModelDiffTest {
  @Test
  fun detectsRootChangeWhenRootIdsChange() {
    val oldModel = buildSessionTreeModel(
      projects = listOf(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true, hasLoaded = true)),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionsTreeUiState(),
    )
    val newModel = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true, hasLoaded = true),
        AgentProjectSessions(path = "/work/project-b", name = "Project B", isOpen = true, hasLoaded = true),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val diff = diffSessionTreeModels(oldModel, newModel)

    assertThat(diff.rootChanged).isTrue()
  }

  @Test
  fun detectsStructureChangesForParentWhenChildrenSetChanges() {
    val oldModel = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Thread 1",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf("/work/project-a" to 10),
      treeUiState = InMemorySessionsTreeUiState(),
    )
    val newModel = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Thread 1",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            ),
            AgentSessionThread(
              id = "thread-2",
              title = "Thread 2",
              updatedAt = 90,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            ),
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf("/work/project-a" to 10),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val diff = diffSessionTreeModels(oldModel, newModel)

    assertThat(diff.rootChanged).isFalse()
    assertThat(diff.structureChangedIds).isEqualTo(setOf(SessionTreeId.Project("/work/project-a")))
    assertThat(diff.contentChangedIds.isEmpty()).isTrue()
  }

  @Test
  fun detectsContentChangesWithoutStructureChanges() {
    val oldModel = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Thread 1",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf("/work/project-a" to 10),
      treeUiState = InMemorySessionsTreeUiState(),
    )
    val newModel = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          hasLoaded = true,
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Renamed",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf("/work/project-a" to 10),
      treeUiState = InMemorySessionsTreeUiState(),
    )

    val diff = diffSessionTreeModels(oldModel, newModel)

    assertThat(diff.rootChanged).isFalse()
    assertThat(diff.structureChangedIds.isEmpty()).isTrue()
    assertThat(diff.contentChangedIds)
      .isEqualTo(setOf(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")))
  }
}
