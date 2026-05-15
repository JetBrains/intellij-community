// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
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
import java.util.Collections
import java.util.concurrent.CompletableFuture

@TestApplication
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
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "thread-2"))
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
        harness.model.entriesById.containsKey(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "thread-1")) &&
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
          ids == listOf(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "thread-1"))
        }
      }
      assertThat(harness.invalidatedDiffs).isEmpty()
    }
    finally {
      runInEdtAndWait { harness.controller.dispose() }
    }
  }

  @Test
  fun contentOnlyDiffWithUnchangedSelectionDoesNotNeedSelectionApply() {
    val selected = listOf(SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "thread-1"))
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

private class ControllerHarness {
  val sessionsState = MutableStateFlow(AgentSessionsState())
  val archivedSessionsState = MutableStateFlow(AgentArchivedSessionsState())
  val threadViewState = MutableStateFlow(AgentSessionThreadViewState())
  val selectedChatTab = MutableStateFlow<AgentChatTabSelection?>(null)
  val invalidatedDiffs: MutableList<SessionTreeModelDiff> = Collections.synchronizedList(mutableListOf<SessionTreeModelDiff>())
  val selectedIds: MutableList<List<SessionTreeId>> = Collections.synchronizedList(mutableListOf<List<SessionTreeId>>())

  @Volatile
  var model: SessionTreeModel = SessionTreeModel.EMPTY

  val controller = AgentSessionsTreeStateController(
    sessionsStateFlow = sessionsState,
    archivedSessionsStateFlow = archivedSessionsState,
    threadViewStateFlow = threadViewState,
    selectedChatTabFlow = selectedChatTab,
    markThreadAsRead = { _, _, _, _ -> },
    ensureArchivedSessionsLoaded = {},
    tree = Tree(),
    getSessionTreeModel = { model },
    setSessionTreeModel = { model = it },
    onLastUsedProviderChanged = {},
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

private fun stateWithThread(threadId: String): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = PROJECT_PATH,
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        threads = listOf(
          AgentSessionThread(
            id = threadId,
            title = threadId,
            updatedAt = 100,
            archived = false,
            provider = AgentSessionProvider.CODEX,
          )
        ),
      )
    ),
    lastUpdatedAt = 1,
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
