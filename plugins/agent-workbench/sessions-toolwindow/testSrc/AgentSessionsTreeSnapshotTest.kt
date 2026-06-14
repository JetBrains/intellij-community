// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatOpenPendingTabsState
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.archiveTargetFromThreadNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.overlayPendingAgentChatTabs
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
      AgentProjectSessions(path = "/work/project-b",
                           name = "Project B",
                           isOpen = false,
                           providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
      AgentProjectSessions(path = "/work/project-open",
                           name = "Project Open",
                           isOpen = true,
                           providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX)),
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

  @Test
  fun projectSearchTextUsesProjectNameAndVisibleBranchWithoutPathFallback() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "feature-x", isOpen = true)

    val searchText = sessionTreeNodeSearchText(SessionTreeNode.Project(project))

    assertThat(searchText).isEqualTo("Project A feature-x")
    assertThat(searchText).doesNotContain("/work/project-a")
  }

  @Test
  fun projectSearchTextIncludesUniqueQualifierForSameNameAndSameBranch() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "feature-x", isOpen = true),
        AgentProjectSessions(path = "/tmp/project-a", name = "Project A", branch = "feature-x", isOpen = true),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )
    val firstNode = model.entriesById.getValue(SessionTreeId.Project("/work/project-a")).node as SessionTreeNode.Project
    val secondNode = model.entriesById.getValue(SessionTreeId.Project("/tmp/project-a")).node as SessionTreeNode.Project

    assertThat(firstNode.pathQualifier).isEqualTo("…/work/project-a")
    assertThat(secondNode.pathQualifier).isEqualTo("…/tmp/project-a")
    assertThat(sessionTreeNodeSearchText(firstNode)).isEqualTo("Project A feature-x …/work/project-a")
  }

  @Test
  fun projectSearchTextIncludesUniqueQualifierForSameNameAndHiddenDefaultBranch() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "main", isOpen = true),
        AgentProjectSessions(path = "/tmp/project-a", name = "Project A", branch = "master", isOpen = true),
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )
    val firstNode = model.entriesById.getValue(SessionTreeId.Project("/work/project-a")).node as SessionTreeNode.Project

    assertThat(firstNode.pathQualifier).isEqualTo("…/work/project-a")
    assertThat(sessionTreeNodeSearchText(firstNode)).isEqualTo("Project A …/work/project-a")
  }

  @Test
  fun pendingAgentChatOverlayAddsProjectThreadWithoutMutatingBaseState() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
      )
    )

    val overlaidState = overlayPendingAgentChatTabs(
      state = state,
      pendingTabsState = pendingTabsState(
        path = "/work/project-a",
        provider = AgentSessionProvider.CODEX,
        threadId = "new-pending",
        pendingCreatedAtMs = 700L,
      ),
    )

    assertThat(state.projects.single().threads).isEmpty()
    val pendingThread = overlaidState.projects.single().threads.single()
    assertThat(pendingThread.id).isEqualTo("new-pending")
    assertThat(pendingThread.title).isEqualTo(AgentSessionsBundle.message("toolwindow.action.new.thread"))
    assertThat(pendingThread.updatedAt).isEqualTo(700L)
    assertThat(pendingThread.activity).isEqualTo(AgentThreadActivity.READY)
    assertThat(pendingThread.provider).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun pendingAgentChatOverlayAddsWorktreeThreadOnlyToMatchingWorktree() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          worktrees = listOf(
            AgentWorktree(path = "/work/project-a-feature", name = "Feature", branch = null, isOpen = true)
          ),
        )
      )
    )

    val overlaidState = overlayPendingAgentChatTabs(
      state = state,
      pendingTabsState = pendingTabsState(
        path = "/work/project-a-feature",
        provider = AgentSessionProvider.CODEX,
        threadId = "new-pending",
        pendingCreatedAtMs = 700L,
      ),
    )

    val project = overlaidState.projects.single()
    assertThat(project.threads).isEmpty()
    assertThat(project.worktrees.single().threads.single().id).isEqualTo("new-pending")
  }

  @Test
  fun pendingAgentChatOverlaySkipsUnknownPathsAndMalformedIdentities() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
      )
    )
    val pendingTabsState = AgentChatOpenPendingTabsState(
      mapOf(
        AgentSessionProvider.CODEX to mapOf(
          "/work/unknown" to listOf(pendingTab(path = "/work/unknown", provider = AgentSessionProvider.CODEX, threadId = "new-pending")),
          "/work/project-a" to listOf(
            pendingTab(path = "/work/project-a", provider = AgentSessionProvider.CODEX, pendingThreadIdentity = "not-a-thread")
          ),
        )
      )
    )

    val overlaidState = overlayPendingAgentChatTabs(state = state, pendingTabsState = pendingTabsState)

    assertThat(overlaidState).isSameAs(state)
    assertThat(overlaidState.projects.single().threads).isEmpty()
  }

  @Test
  fun pendingAgentChatOverlayDeduplicatesByProviderAndThreadId() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
      )
    )
    val pendingTabsState = AgentChatOpenPendingTabsState(
      mapOf(
        AgentSessionProvider.CODEX to mapOf(
          "/work/project-a" to listOf(
            pendingTab(path = "/work/project-a",
                       provider = AgentSessionProvider.CODEX,
                       threadId = "new-pending",
                       pendingCreatedAtMs = 100L),
            pendingTab(path = "/work/project-a",
                       provider = AgentSessionProvider.CODEX,
                       threadId = "new-pending",
                       pendingCreatedAtMs = 200L),
          )
        )
      )
    )

    val pendingThreads = overlayPendingAgentChatTabs(state = state, pendingTabsState = pendingTabsState)
      .projects
      .single()
      .threads

    assertThat(pendingThreads).hasSize(1)
    assertThat(pendingThreads.single().updatedAt).isEqualTo(200L)
  }
}

private fun pendingTabsState(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
  pendingCreatedAtMs: Long,
): AgentChatOpenPendingTabsState {
  return AgentChatOpenPendingTabsState(
    mapOf(
      provider to mapOf(
        path to listOf(
          pendingTab(path = path, provider = provider, threadId = threadId, pendingCreatedAtMs = pendingCreatedAtMs)
        )
      )
    )
  )
}

private fun pendingTab(
  path: String,
  provider: AgentSessionProvider,
  threadId: String = "new-pending",
  pendingThreadIdentity: String = buildAgentThreadIdentity(provider.value, threadId),
  pendingCreatedAtMs: Long? = null,
): AgentChatPendingTabSnapshot {
  return AgentChatPendingTabSnapshot(
    projectPath = path,
    pendingTabKey = "pending-$pendingThreadIdentity",
    pendingThreadIdentity = pendingThreadIdentity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = null,
    pendingLaunchMode = "standard",
  )
}
