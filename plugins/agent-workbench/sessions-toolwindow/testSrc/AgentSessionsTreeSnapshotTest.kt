// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatOpenTabsPresentationState
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
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
        providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
        threads = listOf(
          AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex")),
          AgentSessionThread(id = "thread-2", title = "Thread 2", updatedAt = 90, archived = false, provider = AgentSessionProvider.from("codex")),
        ),
      ),
      AgentProjectSessions(path = "/work/project-b",
                           name = "Project B",
                           isOpen = false,
                           providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex"))),
      AgentProjectSessions(path = "/work/project-open",
                           name = "Project Open",
                           isOpen = true,
                           providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex"))),
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
  fun currentProjectScopedModelKeepsProjectContainer() {
    val projectPath = "/work/project-a"
    val threadId = "thread-1"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
          threads = listOf(
            AgentSessionThread(
              id = threadId,
              title = "Thread 1",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.from("codex"),
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
      currentProjectScopeActive = true,
    )

    val projectTreeId = SessionTreeId.Project(projectPath)
    val threadTreeId = SessionTreeId.Thread(projectPath, AgentSessionProvider.from("codex"), threadId)
    assertThat(model.rootIds).containsExactly(projectTreeId)
    assertThat(model.entriesById.getValue(projectTreeId).childIds).containsExactly(threadTreeId)
    assertThat(model.entriesById.getValue(threadTreeId).parentId).isEqualTo(projectTreeId)
  }

  @Test
  fun pinnedEditorTabThreadsAreMovedToPinnedSectionAboveProjects() {
    val provider = AgentSessionProvider.from("codex")
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(provider),
          threads = listOf(
            AgentSessionThread(id = "recent", title = "Recent", updatedAt = 300, archived = false, provider = provider),
            AgentSessionThread(id = "middle", title = "Middle", updatedAt = 200, archived = false, provider = provider),
            AgentSessionThread(id = "pinned", title = "Pinned", updatedAt = 100, archived = false, provider = provider),
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf(projectPath to 1),
      treeUiState = InMemorySessionTreeUiState(),
      openTabsPresentationState = AgentChatOpenTabsPresentationState(
        pinnedTopLevelThreadIdsByProvider = mapOf(provider to mapOf(projectPath to setOf("pinned"))),
      ),
    )

    assertThat(model.rootIds).containsExactly(SessionTreeId.Pinned, SessionTreeId.Project(projectPath))
    val pinnedEntry = model.entriesById.getValue(SessionTreeId.Pinned)
    assertThat(pinnedEntry.node).isEqualTo(SessionTreeNode.PinnedSection)
    assertThat(pinnedEntry.childIds).containsExactly(SessionTreeId.Thread(projectPath, provider, "pinned"))

    val projectEntry = model.entriesById.getValue(SessionTreeId.Project(projectPath))
    val pinnedThreadId = SessionTreeId.Thread(projectPath, provider, "pinned")
    assertThat(model.entriesById.getValue(pinnedThreadId).parentId).isEqualTo(SessionTreeId.Pinned)
    assertThat(projectEntry.childIds).doesNotContain(pinnedThreadId)
    assertThat(projectEntry.childIds.first()).isEqualTo(SessionTreeId.Thread(projectPath, provider, "recent"))
    assertThat(projectEntry.childIds).contains(SessionTreeId.MoreThreads(projectPath))
    assertThat((model.entriesById.getValue(SessionTreeId.MoreThreads(projectPath)).node as SessionTreeNode.MoreThreads).hiddenCount).isEqualTo(1)
  }

  @Test
  fun currentProjectScopedModelKeepsPinnedSectionAboveProjectContainer() {
    val provider = AgentSessionProvider.from("codex")
    val projectPath = "/work/project-a"
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = projectPath,
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(provider),
          threads = listOf(
            AgentSessionThread(id = "recent", title = "Recent", updatedAt = 300, archived = false, provider = provider),
            AgentSessionThread(id = "pinned", title = "Pinned", updatedAt = 100, archived = false, provider = provider),
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
      currentProjectScopeActive = true,
      openTabsPresentationState = AgentChatOpenTabsPresentationState(
        pinnedTopLevelThreadIdsByProvider = mapOf(provider to mapOf(projectPath to setOf("pinned"))),
      ),
    )

    val projectTreeId = SessionTreeId.Project(projectPath)
    val recentThreadId = SessionTreeId.Thread(projectPath, provider, "recent")
    val pinnedThreadId = SessionTreeId.Thread(projectPath, provider, "pinned")
    assertThat(model.rootIds).containsExactly(SessionTreeId.Pinned, projectTreeId)
    assertThat(model.entriesById.getValue(SessionTreeId.Pinned).childIds).containsExactly(pinnedThreadId)
    assertThat(model.entriesById.getValue(projectTreeId).childIds).containsExactly(recentThreadId)
    assertThat(model.entriesById.getValue(recentThreadId).parentId).isEqualTo(projectTreeId)
    assertThat(model.entriesById.getValue(pinnedThreadId).parentId).isEqualTo(SessionTreeId.Pinned)
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
          providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
          threads = listOf(AgentSessionThread(id = "thread-1",
                                              title = "Thread 1",
                                              updatedAt = 100,
                                              archived = false,
                                              provider = AgentSessionProvider.from("codex"))),
        ),
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
  fun archiveTargetUsesWorktreePathForWorktreeThread() {
    val thread = SessionTreeNode.Thread(
      project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true),
      thread = AgentSessionThread(
        id = "thread-1",
        title = "Thread 1",
        updatedAt = 10,
        archived = false,
        provider = AgentSessionProvider.from("claude"),
      ),
    )

    val target = archiveTargetFromThreadNode(
      id = SessionTreeId.WorktreeThread(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-a-feature",
        provider = AgentSessionProvider.from("claude"),
        threadId = "thread-1",
      ),
      threadNode = thread,
    )

    assertThat(target.path).isEqualTo("/work/project-a-feature")
    assertThat(target.provider).isEqualTo(AgentSessionProvider.from("claude"))
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
      provider = AgentSessionProvider.from("codex"),
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
      provider = AgentSessionProvider.from("codex"),
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
      openTabsPresentationState = pendingTabsState(
        path = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
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
    assertThat(pendingThread.provider).isEqualTo(AgentSessionProvider.from("codex"))
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
      openTabsPresentationState = pendingTabsState(
        path = "/work/project-a-feature",
        provider = AgentSessionProvider.from("codex"),
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
    val pendingTabsState = AgentChatOpenTabsPresentationState(
      mapOf(
        AgentSessionProvider.from("codex") to mapOf(
          "/work/unknown" to listOf(pendingTab(path = "/work/unknown", provider = AgentSessionProvider.from("codex"), threadId = "new-pending")),
          "/work/project-a" to listOf(
            pendingTab(path = "/work/project-a", provider = AgentSessionProvider.from("codex"), pendingThreadIdentity = "not-a-thread")
          ),
        )
      )
    )

    val overlaidState = overlayPendingAgentChatTabs(state = state, openTabsPresentationState = pendingTabsState)

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
    val pendingTabsState = AgentChatOpenTabsPresentationState(
      mapOf(
        AgentSessionProvider.from("codex") to mapOf(
          "/work/project-a" to listOf(
            pendingTab(path = "/work/project-a",
                       provider = AgentSessionProvider.from("codex"),
                       threadId = "new-pending",
                       pendingCreatedAtMs = 100L),
            pendingTab(path = "/work/project-a",
                       provider = AgentSessionProvider.from("codex"),
                       threadId = "new-pending",
                       pendingCreatedAtMs = 200L),
          )
        )
      )
    )

    val pendingThreads = overlayPendingAgentChatTabs(state = state, openTabsPresentationState = pendingTabsState)
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
  pinnedEditorTab: Boolean = false,
): AgentChatOpenTabsPresentationState {
  return AgentChatOpenTabsPresentationState(
    pendingTabsByProvider = mapOf(
      provider to mapOf(
        path to listOf(
          pendingTab(
            path = path,
            provider = provider,
            threadId = threadId,
            pendingCreatedAtMs = pendingCreatedAtMs,
            pinnedEditorTab = pinnedEditorTab,
          )
        )
      )
    ),
    pinnedTopLevelThreadIdsByProvider = if (pinnedEditorTab) mapOf(provider to mapOf(path to setOf(threadId))) else emptyMap(),
  )
}

private fun pendingTab(
  path: String,
  provider: AgentSessionProvider,
  threadId: String = "new-pending",
  pendingThreadIdentity: String = buildAgentThreadIdentity(provider.value, threadId),
  pendingCreatedAtMs: Long? = null,
  pinnedEditorTab: Boolean = false,
): AgentChatPendingTabSnapshot {
  return AgentChatPendingTabSnapshot(
    projectPath = path,
    pendingTabKey = "pending-$pendingThreadIdentity",
    pendingThreadIdentity = pendingThreadIdentity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = null,
    pendingLaunchMode = "standard",
    pinnedEditorTab = pinnedEditorTab,
  )
}
