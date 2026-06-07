// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions as ModelAgentProjectSessions
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.resolveSelectedSessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.ui.SessionTreeRebuildReason
import com.intellij.agent.workbench.sessions.toolwindow.ui.sessionTreeSelectionTargetsAfterModelSwap
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class SessionTreeSelectionSyncTest {
  @Test
  fun resolvesProjectThreadSelection() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
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
    )

    val selection = AgentChatTabSelection(
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      subAgentId = null,
    )

    val selectedId = resolveSelectedSessionTreeId(projects, selection)

    assertThat(selectedId).isEqualTo(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"))
  }

  @Test
  fun resolvesProjectSubAgentSelectionWithThreadFallback() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(
          AgentSessionThread(
            id = "thread-1",
            title = "Thread 1",
            updatedAt = 100,
            archived = false,
            provider = AgentSessionProvider.CODEX,
            subAgents = listOf(AgentSubAgent(id = "alpha", name = "Alpha")),
          )
        ),
      )
    )

    val selectedSubAgent = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = "alpha",
      ),
    )
    val fallbackToThread = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = "beta",
      ),
    )

    assertThat(selectedSubAgent)
      .isEqualTo(SessionTreeId.SubAgent("/work/project-a", AgentSessionProvider.CODEX, "thread-1", "alpha"))
    assertThat(fallbackToThread).isEqualTo(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"))
  }

  @Test
  fun resolvesWorktreeThreadAndSubAgentSelection() {
    val worktreeThread = AgentSessionThread(
      id = "thread-wt",
      title = "Worktree Thread",
      updatedAt = 200,
      archived = false,
      provider = AgentSessionProvider.CLAUDE,
      subAgents = listOf(AgentSubAgent(id = "agent-1", name = "Agent 1")),
    )
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature",
            isOpen = false,
            threads = listOf(worktreeThread),
          )
        ),
      )
    )

    val threadSelection = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-feature",
        threadIdentity = "claude:thread-wt",
        threadId = "thread-wt",
        subAgentId = null,
      ),
    )
    val subAgentSelection = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-feature",
        threadIdentity = "claude:thread-wt",
        threadId = "thread-wt",
        subAgentId = "agent-1",
      ),
    )

    assertThat(threadSelection)
      .isEqualTo(SessionTreeId.WorktreeThread("/work/project-a", "/work/project-feature", AgentSessionProvider.CLAUDE, "thread-wt"))
    assertThat(subAgentSelection)
      .isEqualTo(
        SessionTreeId.WorktreeSubAgent(
          "/work/project-a",
          "/work/project-feature",
          AgentSessionProvider.CLAUDE,
          "thread-wt",
          "agent-1",
        )
      )
  }

  @Test
  fun returnsNullForUnknownOrMalformedSelection() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
      )
    )

    val malformedIdentity = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "malformed",
        threadId = "thread-1",
        subAgentId = null,
      ),
    )
    val unknownPath = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/missing",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = null,
      ),
    )

    assertThat(malformedIdentity).isNull()
    assertThat(unknownPath).isNull()
  }

  @Test
  fun stateRefreshPreservesUserSelectionOverActiveChat() {
    val model = modelForSelectionTests(projectWithThreads("thread-1", "thread-2"))
    val userSelection = listOf(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-2"))
    val activeChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
      previouslySelectedTreeIds = userSelection,
      selectedChatTreeId = activeChat,
    )

    assertThat(selection).containsExactlyElementsOf(userSelection)
  }

  @Test
  fun chatTabSelectionSelectsActiveChatRow() {
    val model = modelForSelectionTests(projectWithThreads("thread-1", "thread-2"))
    val userSelection = listOf(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-2"))
    val activeChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED,
      previouslySelectedTreeIds = userSelection,
      selectedChatTreeId = activeChat,
    )

    assertThat(selection).containsExactly(activeChat)
  }

  @Test
  fun stateRefreshPreservesMultiSelectionAndDropsRemovedIds() {
    val model = modelForSelectionTests(projectWithThreads("thread-1"))
    val survivingSelection = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")
    val removedSelection = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-2")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
      previouslySelectedTreeIds = listOf(survivingSelection, removedSelection),
      selectedChatTreeId = null,
    )

    assertThat(selection).containsExactly(survivingSelection)
  }

  @Test
  fun stateRefreshFallsBackToActiveChatWhenNoSelectionSurvives() {
    val model = modelForSelectionTests(projectWithThreads("thread-1"))
    val removedSelection = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-2")
    val activeChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
      previouslySelectedTreeIds = listOf(removedSelection),
      selectedChatTreeId = activeChat,
    )

    assertThat(selection).containsExactly(activeChat)
  }

  @Test
  fun stateRefreshFallsBackToActiveChatWhenNoSelectionWasEverApplied() {
    val model = modelForSelectionTests(projectWithThreads("thread-1"))
    val activeChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
      previouslySelectedTreeIds = emptyList(),
      selectedChatTreeId = activeChat,
      selectionInitialized = true,
      lastAppliedSelectedTreeIds = emptyList(),
    )

    assertThat(selection).containsExactly(activeChat)
  }

  @Test
  fun stateRefreshPreservesDeliberatelyClearedSelection() {
    val model = modelForSelectionTests(projectWithThreads("thread-1"))
    val activeChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
      previouslySelectedTreeIds = emptyList(),
      selectedChatTreeId = activeChat,
      selectionInitialized = true,
      lastAppliedSelectedTreeIds = listOf(activeChat),
    )

    assertThat(selection).isEmpty()
  }

  @Test
  fun unresolvedChatSelectionDoesNotClearUserSelection() {
    val model = modelForSelectionTests(projectWithThreads("thread-1"))
    val userSelection = listOf(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"))
    val unresolvedActiveChat = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "missing")

    val selection = sessionTreeSelectionTargetsAfterModelSwap(
      model = model,
      reason = SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED,
      previouslySelectedTreeIds = userSelection,
      selectedChatTreeId = unresolvedActiveChat,
    )

    assertThat(selection).containsExactlyElementsOf(userSelection)
  }
}

private fun modelForSelectionTests(vararg projects: ModelAgentProjectSessions) =
  buildSessionTreeModel(
    projects = projects.toList(),
    visibleClosedProjectCount = Int.MAX_VALUE,
    visibleThreadCounts = buildMap {
      projects.forEach { project ->
        put(project.path, 10)
        project.worktrees.forEach { worktree -> put(worktree.path, 10) }
      }
    },
    treeUiState = InMemorySessionTreeUiState(),
  )

private fun projectWithThreads(vararg threadIds: String): ModelAgentProjectSessions {
  return AgentProjectSessions(
    path = "/work/project-a",
    name = "Project A",
    isOpen = true,
    providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
    threads = threadIds.map { threadId ->
      AgentSessionThread(
        id = threadId,
        title = threadId,
        updatedAt = 100,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
    },
  )
}
