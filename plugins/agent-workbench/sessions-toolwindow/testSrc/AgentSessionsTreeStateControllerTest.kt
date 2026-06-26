// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatOpenPendingTabsState
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModelDiff
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsTreeStateController
import com.intellij.agent.workbench.sessions.toolwindow.ui.shouldApplySelectionAfterModelSwap
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.Collections
import java.util.concurrent.CompletableFuture

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsTreeStateControllerTest {
  @Test
  fun hiddenStateUpdatesAreDeferredUntilVisible() = runBlocking {
    val harness = createHarness()
    try {
      runInEdtAndWait {
        harness.controller.setModelUpdatesVisible(false)
        harness.controller.start()
      }

      harness.sessionsState.value = stateWithThread("thread-1")
      harness.sessionsState.value = stateWithThread("thread-2")

      waitForCondition {
        harness.controller.hasPendingModelUpdateForTest() &&
        harness.controller.displayedStateSnapshot().projects.firstOrNull()?.threads?.singleOrNull()?.id == "thread-2"
      }
      assertThat(harness.invalidatedDiffs).isEmpty()

      runInEdtAndWait { harness.controller.setModelUpdatesVisible(true) }

      waitForCondition {
        harness.invalidatedDiffs.size == 1 &&
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "thread-2"))
      }
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun hiddenChatSelectionIsAppliedWhenVisibleAgain() = runBlocking {
    val harness = createHarness()
    try {
      runInEdtAndWait { harness.controller.start() }
      harness.sessionsState.value = stateWithThread("thread-1")

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "thread-1")) &&
        harness.selectedIds.isNotEmpty()
      }
      harness.selectedIds.clear()
      harness.invalidatedDiffs.clear()

      runInEdtAndWait { harness.controller.setModelUpdatesVisible(false) }
      harness.selectedChatTab.value = AgentChatTabSelection(
        projectPath = PROJECT_PATH,
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = null,
      )

      waitForCondition { harness.controller.hasPendingModelUpdateForTest() }
      assertThat(harness.selectedIds).isEmpty()
      assertThat(harness.invalidatedDiffs).isEmpty()

      runInEdtAndWait { harness.controller.setModelUpdatesVisible(true) }

      waitForCondition {
        harness.selectedIds.any { ids ->
          ids == listOf(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "thread-1"))
        }
      }
      assertThat(harness.invalidatedDiffs).isEmpty()
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun pendingChatTabsAreDisplayedAsActiveOverlayWithoutMutatingStoredState() = runBlocking {
    val harness = createHarness()
    try {
      runInEdtAndWait { harness.controller.start() }
      harness.sessionsState.value = openProjectStateWithoutThreads()

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Project(PROJECT_PATH)) &&
        !harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "new-pending"))
      }

      harness.pendingChatTabsState.value = pendingState()

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "new-pending"))
      }
      assertThat(harness.controller.displayedStateSnapshot().projects.single().threads.single().id).isEqualTo("new-pending")
      assertThat(harness.sessionsState.value.projects.single().threads).isEmpty()

      harness.pendingChatTabsState.value = AgentChatOpenPendingTabsState.EMPTY

      waitForCondition {
        !harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "new-pending"))
      }
      assertThat(harness.sessionsState.value.projects.single().threads).isEmpty()
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun archivedViewIgnoresPendingChatTabsOverlay() = runBlocking {
    val harness = createHarness()
    try {
      runInEdtAndWait { harness.controller.start() }
      harness.sessionsState.value = openProjectStateWithoutThreads()
      harness.pendingChatTabsState.value = pendingState()

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "new-pending"))
      }

      harness.threadViewState.value = AgentSessionThreadViewState(
        mode = AgentSessionThreadViewMode.ARCHIVED,
        archivedRangePreset = AgentSessionArchivedRangePreset.ALL,
      )

      waitForCondition {
        !harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "new-pending")) &&
        harness.controller.displayedStateSnapshot().projects.isEmpty()
      }
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun currentProjectScopeFiltersActiveTreeToCurrentProject() {
    runBlocking {
      val harness = createHarness()
      try {
        harness.currentProjectOnly = true
        harness.currentProjectPath = PROJECT_PATH
        runInEdtAndWait { harness.controller.start() }

        harness.sessionsState.value = AgentSessionsState(
          projects = listOf(
            projectWithThread(PROJECT_PATH, "Project A", "current-thread"),
            projectWithThread(OTHER_PROJECT_PATH, "Project B", "other-thread"),
          ),
          lastUpdatedAt = 1,
        )

        waitForCondition {
          harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "current-thread")) &&
          !harness.model.entriesById.containsKey(SessionTreeId.Thread(OTHER_PROJECT_PATH, AgentSessionProvider.from("codex"), "other-thread"))
        }
        assertThat(harness.controller.displayedStateSnapshot().projects.map { it.path }).containsExactly(PROJECT_PATH)
        assertThat(harness.model.rootIds)
          .containsExactly(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "current-thread"))
        assertThat(harness.model.entriesById).doesNotContainKey(SessionTreeId.Project(PROJECT_PATH))
      }
      finally {
        runInEdtAndWait { harness.controller.dispose() }
      }
    }
  }

  @Test
  fun currentProjectScopeChangeRebuildsTree() = runBlocking {
    val harness = createHarness()
    try {
      harness.currentProjectPath = PROJECT_PATH
      runInEdtAndWait { harness.controller.start() }
      harness.sessionsState.value = AgentSessionsState(
        projects = listOf(
          projectWithThread(PROJECT_PATH, "Project A", "current-thread"),
          projectWithThread(OTHER_PROJECT_PATH, "Project B", "other-thread"),
        ),
        lastUpdatedAt = 1,
      )

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Thread(OTHER_PROJECT_PATH, AgentSessionProvider.from("codex"), "other-thread"))
      }

      harness.currentProjectOnly = true
      runInEdtAndWait { harness.controller.projectScopeChanged() }

      waitForCondition {
        !harness.model.entriesById.containsKey(SessionTreeId.Thread(OTHER_PROJECT_PATH, AgentSessionProvider.from("codex"), "other-thread")) &&
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "current-thread"))
      }
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun currentProjectScopePromotesOnlyMatchingWorktreeToRoot() {
    runBlocking {
      val harness = createHarness()
      try {
        harness.currentProjectOnly = true
        harness.currentProjectPath = WORKTREE_PATH
        runInEdtAndWait { harness.controller.start() }
        harness.sessionsState.value = AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
              threads = listOf(thread("parent-thread")),
              worktrees = listOf(
                AgentWorktree(
                  path = WORKTREE_PATH,
                  name = "feature",
                  branch = "feature",
                  isOpen = true,
                  threads = listOf(thread("worktree-thread")),
                ),
                AgentWorktree(
                  path = OTHER_WORKTREE_PATH,
                  name = "other",
                  branch = "other",
                  isOpen = false,
                  threads = listOf(thread("other-worktree-thread")),
                ),
              ),
            ),
          ),
          lastUpdatedAt = 1,
        )

        waitForCondition {
          harness.model.entriesById.containsKey(SessionTreeId.WorktreeThread(PROJECT_PATH,
                                                                             WORKTREE_PATH,
                                                                             AgentSessionProvider.from("codex"),
                                                                             "worktree-thread"))
        }
        assertThat(harness.model.rootIds).containsExactly(SessionTreeId.Worktree(PROJECT_PATH, WORKTREE_PATH))
        assertThat(harness.model.entriesById).doesNotContainKey(SessionTreeId.Project(PROJECT_PATH))
        assertThat(harness.model.entriesById).doesNotContainKey(SessionTreeId.Thread(PROJECT_PATH,
                                                                                     AgentSessionProvider.from("codex"),
                                                                                     "parent-thread"))
        assertThat(harness.model.entriesById)
          .doesNotContainKey(SessionTreeId.WorktreeThread(PROJECT_PATH,
                                                          OTHER_WORKTREE_PATH,
                                                          AgentSessionProvider.from("codex"),
                                                          "other-worktree-thread"))
        assertThat(harness.controller.displayedStateSnapshot().projects.single().worktrees.map { it.path }).containsExactly(WORKTREE_PATH)
      }
      finally {
        runInEdtAndWait { harness.controller.dispose() }
      }
    }
  }

  @Test
  fun currentProjectScopeFiltersArchivedTreeToCurrentProject() = runBlocking {
    val harness = createHarness()
    try {
      harness.currentProjectOnly = true
      harness.currentProjectPath = PROJECT_PATH
      runInEdtAndWait { harness.controller.start() }
      harness.archivedSessionsState.value = AgentArchivedSessionsState(
        projects = listOf(
          projectWithThread(PROJECT_PATH, "Project A", "current-archived"),
          projectWithThread(OTHER_PROJECT_PATH, "Project B", "other-archived"),
        ),
        lastUpdatedAt = 1,
      )
      harness.threadViewState.value = AgentSessionThreadViewState(
        mode = AgentSessionThreadViewMode.ARCHIVED,
        archivedRangePreset = AgentSessionArchivedRangePreset.ALL,
      )

      waitForCondition {
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "current-archived")) &&
        !harness.model.entriesById.containsKey(SessionTreeId.Thread(OTHER_PROJECT_PATH, AgentSessionProvider.from("codex"), "other-archived"))
      }
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun contentOnlyDiffWithUnchangedSelectionDoesNotNeedSelectionApply() {
    val selected = listOf(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.from("codex"), "thread-1"))
    val diff = SessionTreeModelDiff(
      rootChanged = false,
      structureChangedIds = emptySet(),
      contentChangedIds = setOf(selected.single()),
    )

    assertThat(
      shouldApplySelectionAfterModelSwap(
        treeModelDiff = diff,
        selectedTreeIds = selected,
        selectedTreeIdsBeforeModelSwap = selected,
        treeSelectionInitialized = true,
        lastAppliedSelectedTreeIds = selected,
      )
    ).isFalse()
  }

  private fun createHarness(): ControllerHarness {
    var harness: ControllerHarness? = null
    runInEdtAndWait { harness = ControllerHarness() }
    return checkNotNull(harness)
  }
}

private const val PROJECT_PATH = "/work/project-a"
private const val OTHER_PROJECT_PATH = "/work/project-b"
private const val WORKTREE_PATH = "/work/project-a-feature"
private const val OTHER_WORKTREE_PATH = "/work/project-a-other"

private class ControllerHarness {
  val sessionsState = MutableStateFlow(AgentSessionsState())
  val archivedSessionsState = MutableStateFlow(AgentArchivedSessionsState())
  val threadViewState = MutableStateFlow(AgentSessionThreadViewState())
  val selectedChatTab = MutableStateFlow<AgentChatTabSelection?>(null)
  val pendingChatTabsState = MutableStateFlow(AgentChatOpenPendingTabsState.EMPTY)
  val invalidatedDiffs: MutableList<SessionTreeModelDiff> = Collections.synchronizedList(mutableListOf<SessionTreeModelDiff>())
  val selectedIds: MutableList<List<SessionTreeId>> = Collections.synchronizedList(mutableListOf<List<SessionTreeId>>())

  @Volatile
  var model: SessionTreeModel = SessionTreeModel.EMPTY

  @Volatile
  var currentProjectOnly: Boolean = false

  @Volatile
  var currentProjectPath: String? = PROJECT_PATH

  val controller = AgentSessionsTreeStateController(
    sessionsStateFlow = sessionsState,
    archivedSessionsStateFlow = archivedSessionsState,
    threadViewStateFlow = threadViewState,
    selectedChatTabFlow = selectedChatTab,
    pendingChatTabsStateFlow = pendingChatTabsState,
    ensureArchivedSessionsLoaded = {},
    tree = Tree(),
    getSessionTreeModel = { model },
    setSessionTreeModel = { model = it },
    onNewThreadProfileMenuChanged = {},
    isCurrentProjectScopeEnabled = { currentProjectOnly },
    currentProjectPathProvider = { currentProjectPath },
    onBeforeModelSwap = {},
    invalidateTreeModel = { diff ->
      invalidatedDiffs += diff
      CompletableFuture.completedFuture(null)
    },
    expandNode = {},
    selectNodes = { ids, shouldApply, _, onApplied ->
      if (shouldApply()) {
        selectedIds += ids
        onApplied(ids)
      }
    },
  )
}

private fun stateWithThread(
  threadId: String,
  activity: AgentThreadActivity = AgentThreadActivity.READY,
  updatedAt: Long = 100,
): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = PROJECT_PATH,
        name = "Project A",
        isOpen = true,
        providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
        threads = listOf(
          AgentSessionThread(
            id = threadId,
            title = threadId,
            updatedAt = updatedAt,
            archived = false,
            provider = AgentSessionProvider.from("codex"),
            activity = activity,
          )
        ),
      )
    ),
    lastUpdatedAt = 1,
  )
}

private fun openProjectStateWithoutThreads(): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = PROJECT_PATH,
        name = "Project A",
        isOpen = true,
      )
    ),
    lastUpdatedAt = 1,
  )
}

private fun projectWithThread(path: String, name: String, threadId: String): AgentProjectSessions {
  return AgentProjectSessions(
    path = path,
    name = name,
    isOpen = true,
    providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
    threads = listOf(thread(threadId)),
  )
}

private fun thread(
  threadId: String,
  activity: AgentThreadActivity = AgentThreadActivity.READY,
  updatedAt: Long = 100,
): AgentSessionThread {
  return AgentSessionThread(
    id = threadId,
    title = threadId,
    updatedAt = updatedAt,
    archived = false,
    provider = AgentSessionProvider.from("codex"),
    activity = activity,
  )
}

private fun pendingState(): AgentChatOpenPendingTabsState {
  val threadId = "new-pending"
  return AgentChatOpenPendingTabsState(
    mapOf(
      AgentSessionProvider.from("codex") to mapOf(
        PROJECT_PATH to listOf(
          AgentChatPendingTabSnapshot(
            projectPath = PROJECT_PATH,
            pendingTabKey = "pending-$threadId",
            pendingThreadIdentity = buildAgentThreadIdentity(AgentSessionProvider.from("codex").value, threadId),
            pendingCreatedAtMs = 700L,
            pendingFirstInputAtMs = null,
            pendingLaunchMode = "standard",
          )
        )
      )
    )
  )
}

private suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) return
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
