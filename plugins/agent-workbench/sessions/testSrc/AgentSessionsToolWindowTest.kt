// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class AgentSessionsToolWindowTest {
  @get:Rule
  val composeRule: ComposeContentTestRule = createComposeRule()

  @Test
  fun emptyStateIsShownWhenNoProjects() {
    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = emptyList(), lastUpdatedAt = 1L),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.global"))
      .assertIsDisplayed()
  }

  @Test
  fun claudeQuotaHintIsShownWhenRequested() {
    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = emptyList(), lastUpdatedAt = 1L),
        onRefresh = {},
        onOpenProject = {},
        showClaudeQuotaHint = true,
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.title"))
      .assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.body"))
      .assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.enable"))
      .assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.dismiss"))
      .assertIsDisplayed()
  }

  @Test
  fun claudeQuotaHintEnableInvokesCallback() {
    var enabled = false

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = emptyList(), lastUpdatedAt = 1L),
        onRefresh = {},
        onOpenProject = {},
        showClaudeQuotaHint = true,
        onEnableClaudeQuotaWidget = { enabled = true },
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.enable"))
      .performClick()

    composeRule.runOnIdle {
      assertThat(enabled).isTrue()
    }
  }

  @Test
  fun claudeQuotaHintDismissInvokesCallback() {
    var dismissed = false

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = emptyList(), lastUpdatedAt = 1L),
        onRefresh = {},
        onOpenProject = {},
        showClaudeQuotaHint = true,
        onDismissClaudeQuotaHint = { dismissed = true },
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.claude.quota.hint.dismiss"))
      .performClick()

    composeRule.runOnIdle {
      assertThat(dismissed).isTrue()
    }
  }

  @Test
  fun projectsDoNotShowGlobalEmptyStateWhenNoThreadsLoadedYet() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
      ),
      AgentProjectSessions(
        path = "/work/project-b",
        name = "Project B",
        isOpen = false,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onNodeWithText("Project B").assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.empty.global"))
      .assertCountEquals(0)
  }

  @Test
  fun projectsShowThreadsWithoutInlineOpenAction() {
    val now = 1_700_000_000_000L
    val thread = AgentSessionThread(id = "thread-1", title = "Thread One", updatedAt = now - 10 * 60 * 1000L, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(thread),
      ),
      AgentProjectSessions(
        path = "/work/project-b",
        name = "Project B",
        isOpen = false,
        threads = emptyList(),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onNodeWithText("Project B").assertIsDisplayed()
    composeRule.onNodeWithText("Thread One").assertIsDisplayed()
    composeRule.onNodeWithText("10m").assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.action.open"))
      .assertCountEquals(0)
  }

  @Test
  fun openProjectsDoNotShowOpenBadge() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-open",
        name = "Project Open",
        isOpen = true,
      ),
      AgentProjectSessions(
        path = "/work/project-closed",
        name = "Project Closed",
        isOpen = false,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Project Open").assertIsDisplayed()
    composeRule.onNodeWithText("Project Closed").assertIsDisplayed()
    composeRule.onAllNodesWithText("OPEN")
      .assertCountEquals(0)
  }

  @Test
  fun allOpenProjectsRemainVisibleWhenClosedQuotaIsReached() {
    val projects = listOf(
      AgentProjectSessions(path = "/work/project-1", name = "Project 1", isOpen = false),
      AgentProjectSessions(path = "/work/project-2", name = "Project 2", isOpen = false),
      AgentProjectSessions(path = "/work/project-3", name = "Project 3", isOpen = false),
      AgentProjectSessions(path = "/work/project-open-a", name = "Project Open A", isOpen = true),
      AgentProjectSessions(path = "/work/project-4", name = "Project 4", isOpen = false),
      AgentProjectSessions(path = "/work/project-open-b", name = "Project Open B", isOpen = true),
      AgentProjectSessions(path = "/work/project-5", name = "Project 5", isOpen = false),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        visibleClosedProjectCount = 3,
      )
    }

    composeRule.onNodeWithText("Project 1").assertIsDisplayed()
    composeRule.onNodeWithText("Project 2").assertIsDisplayed()
    composeRule.onNodeWithText("Project 3").assertIsDisplayed()
    composeRule.onNodeWithText("Project Open A").assertIsDisplayed()
    composeRule.onNodeWithText("Project Open B").assertIsDisplayed()
    composeRule.onAllNodesWithText("Project 4").assertCountEquals(0)
    composeRule.onAllNodesWithText("Project 5").assertCountEquals(0)
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.action.more.count", 2))
      .assertIsDisplayed()
  }

  @Test
  fun hoveringProjectRowShowsQuickCreateSessionActionAndDoesNotInvokeOpenCallback() {
    var createdSessionPath: String? = null
    var createdSessionProvider: AgentSessionProvider? = null
    var createdSessionMode: AgentSessionLaunchMode? = null
    var openedPath: String? = null
    val projectPath = "/work/project-plus"
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = "Project Plus",
        isOpen = false,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = { openedPath = it },
        onCreateSession = { path, provider, mode ->
          createdSessionPath = path
          createdSessionProvider = provider
          createdSessionMode = mode
        },
        lastUsedProvider = AgentSessionProvider.CLAUDE,
      )
    }

    val quickActionLabel = providerDisplayName(AgentSessionProvider.CLAUDE)
    composeRule.onAllNodesWithContentDescription(quickActionLabel).assertCountEquals(0)

    composeRule.onNodeWithText("Project Plus")
      .assertIsDisplayed()
      .performMouseInput { moveTo(center) }

    composeRule.onNodeWithContentDescription(quickActionLabel)
      .assertIsDisplayed()
      .performClick()

    composeRule.runOnIdle {
      assertThat(createdSessionPath).isEqualTo(projectPath)
      assertThat(createdSessionProvider).isEqualTo(AgentSessionProvider.CLAUDE)
      assertThat(createdSessionMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
      assertThat(openedPath).isNull()
    }
  }

  @Test
  fun hoveringProjectRowWithLongTitleKeepsQuickCreateSessionActionAccessible() {
    var createdSessionPath: String? = null
    var createdSessionProvider: AgentSessionProvider? = null
    var createdSessionMode: AgentSessionLaunchMode? = null
    val projectPath = "/work/project-plus"
    val longProjectName = "Project Plus ".repeat(12).trim()
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = longProjectName,
        branch = "main",
        isOpen = false,
        worktrees = listOf(
          AgentWorktree(
            path = "$projectPath/worktree",
            name = "project-plus-worktree",
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        onCreateSession = { path, provider, mode ->
          createdSessionPath = path
          createdSessionProvider = provider
          createdSessionMode = mode
        },
        lastUsedProvider = AgentSessionProvider.CLAUDE,
      )
    }

    val quickActionLabel = providerDisplayName(AgentSessionProvider.CLAUDE)
    composeRule.onAllNodesWithContentDescription(quickActionLabel).assertCountEquals(0)

    composeRule.onNodeWithText("Project Plus", substring = true)
      .assertIsDisplayed()
      .performMouseInput { moveTo(center) }

    composeRule.onNodeWithContentDescription(quickActionLabel)
      .assertIsDisplayed()
      .performClick()

    composeRule.runOnIdle {
      assertThat(createdSessionPath).isEqualTo(projectPath)
      assertThat(createdSessionProvider).isEqualTo(AgentSessionProvider.CLAUDE)
      assertThat(createdSessionMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    }
  }

  @Test
  fun hoveringWorktreeRowWithLongTitleKeepsQuickCreateSessionActionAccessible() {
    var createdSessionPath: String? = null
    var createdSessionProvider: AgentSessionProvider? = null
    var createdSessionMode: AgentSessionLaunchMode? = null
    val now = 1_700_000_000_000L
    val worktreePath = "/work/project-feature"
    val longWorktreeName = "project-feature-long-name-".repeat(8).trimEnd('-')
    val worktreeThread = AgentSessionThread(id = "wt-thread-1", title = "WT Thread", updatedAt = now, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = worktreePath,
            name = longWorktreeName,
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
            threads = listOf(worktreeThread),
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        onCreateSession = { path, provider, mode ->
          createdSessionPath = path
          createdSessionProvider = provider
          createdSessionMode = mode
        },
        lastUsedProvider = AgentSessionProvider.CLAUDE,
      )
    }

    val quickActionLabel = providerDisplayName(AgentSessionProvider.CLAUDE)
    composeRule.onAllNodesWithContentDescription(quickActionLabel).assertCountEquals(0)

    composeRule.onNodeWithText("project-feature-long-name", substring = true)
      .assertIsDisplayed()
      .performMouseInput { moveTo(center) }

    composeRule.onNodeWithContentDescription(quickActionLabel)
      .assertIsDisplayed()
      .performClick()

    composeRule.runOnIdle {
      assertThat(createdSessionPath).isEqualTo(worktreePath)
      assertThat(createdSessionProvider).isEqualTo(AgentSessionProvider.CLAUDE)
      assertThat(createdSessionMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    }
  }

  @Test
  fun threadRowShowsProviderMarkerForClaude() {
    val now = 1_700_000_000_000L
    val thread = AgentSessionThread(
      id = "session-1",
      title = "Session One",
      updatedAt = now,
      archived = false,
      provider = AgentSessionProvider.CLAUDE,
    )
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(thread),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Session One").assertIsDisplayed()
    composeRule.onNodeWithText(providerDisplayName(AgentSessionProvider.CLAUDE)).assertIsDisplayed()
  }

  @Test
  fun projectErrorShowsRetryAction() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-c",
        name = "Project C",
        isOpen = true,
        errorMessage = "Failed",
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Failed").assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.error.retry"))
      .assertIsDisplayed()
  }

  @Test
  fun projectWarningIsShownWithoutRetryAction() {
    val now = 1_700_000_000_000L
    val thread = AgentSessionThread(id = "thread-1", title = "Thread One", updatedAt = now, archived = false)
    val warningMessage = providerUnavailableMessage(AgentSessionProvider.CODEX)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        threads = listOf(thread),
        providerWarnings = listOf(
          AgentSessionProviderWarning(
            provider = AgentSessionProvider.CODEX,
            message = warningMessage,
          )
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Thread One").assertIsDisplayed()
    composeRule.onNodeWithText(warningMessage).assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.error.retry")).assertCountEquals(0)
  }

  @Test
  fun projectErrorTakesPrecedenceOverProviderWarning() {
    val warningMessage = providerUnavailableMessage(AgentSessionProvider.CLAUDE)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-c",
        name = "Project C",
        isOpen = true,
        hasLoaded = true,
        errorMessage = "Failed",
        providerWarnings = listOf(
          AgentSessionProviderWarning(
            provider = AgentSessionProvider.CLAUDE,
            message = warningMessage,
          )
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Failed").assertIsDisplayed()
    composeRule.onAllNodesWithText(warningMessage).assertCountEquals(0)
  }

  @Test
  fun worktreeWarningIsShownWhenWorktreeHasNoThreads() {
    val warningMessage = providerUnavailableMessage(AgentSessionProvider.CLAUDE)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
            providerWarnings = listOf(
              AgentSessionProviderWarning(
                provider = AgentSessionProvider.CLAUDE,
                message = warningMessage,
              )
            ),
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("project-feature").assertIsDisplayed()
      .performMouseInput { doubleClick() }
    composeRule.onNodeWithText(warningMessage).assertIsDisplayed()
  }

  @Test
  fun openLoadedEmptyProjectIsExpandedByDefault() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertIsDisplayed()
  }

  @Test
  fun collapsedProjectInUiStateIsNotAutoExpandedAndExpandingClearsCollapsedFlag() {
    val projectPath = "/work/project-a"
    val treeUiState = InMemorySessionsTreeUiState()
    treeUiState.setProjectCollapsed(projectPath, collapsed = true)
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        treeUiState = treeUiState,
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertCountEquals(0)

    composeRule.onNodeWithText("Project A")
      .assertIsDisplayed()
      .performMouseInput { doubleClick() }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertIsDisplayed()
    composeRule.runOnIdle {
      assertThat(treeUiState.isProjectCollapsed(projectPath)).isFalse()
    }
  }

  @Test
  fun collapsedProjectInPersistentUiStateRemainsCollapsedAfterContentRefresh() {
    val projectPath = "/work/project-a"
    val treeUiState = AgentSessionsTreeUiStateService()
    val projects = listOf(
      AgentProjectSessions(
        path = projectPath,
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects, lastUpdatedAt = 1L),
        onRefresh = {},
        onOpenProject = {},
        treeUiState = treeUiState,
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertIsDisplayed()
    composeRule.onNodeWithText("Project A")
      .assertIsDisplayed()
      .performMouseInput { doubleClick() }
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertCountEquals(0)
    composeRule.runOnIdle {
      assertThat(treeUiState.isProjectCollapsed(projectPath)).isTrue()
    }

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects, lastUpdatedAt = 2L),
        onRefresh = {},
        onOpenProject = {},
        treeUiState = treeUiState,
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertCountEquals(0)
    composeRule.onNodeWithText("Project A")
      .assertIsDisplayed()
      .performMouseInput { doubleClick() }
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertIsDisplayed()
    composeRule.runOnIdle {
      assertThat(treeUiState.isProjectCollapsed(projectPath)).isFalse()
    }
  }

  @Test
  fun expandingLoadedEmptyProjectShowsEmptyChildRow() {
    val now = 1_700_000_000_000L
    val thread = AgentSessionThread(id = "thread-1", title = "Thread One", updatedAt = now - 10 * 60 * 1000L, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(thread),
        hasLoaded = true,
      ),
      AgentProjectSessions(
        path = "/work/project-b",
        name = "Project B",
        isOpen = false,
        threads = emptyList(),
        hasLoaded = true,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Project B")
      .assertIsDisplayed()
      .performMouseInput { doubleClick() }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.empty.project"))
      .assertIsDisplayed()
  }

  @Test
  fun moreProjectsRowShownWhenProjectsExceedLimit() {
    val projects = (1..12).map { i ->
      AgentProjectSessions(
        path = "/work/project-$i",
        name = "Project $i",
        isOpen = false,
      )
    }

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        visibleClosedProjectCount = 10,
      )
    }

    composeRule.onNodeWithText("Project 1").assertIsDisplayed()
    composeRule.onNodeWithText("Project 10").assertIsDisplayed()
    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.action.more.count", 2))
      .assertIsDisplayed()
    composeRule.onAllNodesWithText("Project 11").assertCountEquals(0)
    composeRule.onAllNodesWithText("Project 12").assertCountEquals(0)
  }

  @Test
  fun threadMoreRowShowsPlainMoreWhenCountIsUnknown() {
    val now = 1_700_000_000_000L
    val threads = (1..5).map { i ->
      AgentSessionThread(
        id = "thread-$i",
        title = "Thread $i",
        updatedAt = now - i,
        archived = false,
      )
    }
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        hasUnknownThreadCount = true,
        threads = threads,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText(AgentSessionsBundle.message("toolwindow.action.more"))
      .assertIsDisplayed()
    composeRule.onAllNodesWithText(AgentSessionsBundle.message("toolwindow.action.more.count", 2))
      .assertCountEquals(0)
  }

  @Test
  fun worktreeShownWhenProjectHasWorktrees() {
    val now = 1_700_000_000_000L
    val worktreeThread = AgentSessionThread(
      id = "wt-thread-1",
      title = "Worktree Thread",
      updatedAt = now - 5 * 60 * 1000L,
      archived = false,
    )
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = emptyList(),
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature-x",
            isOpen = false,
            threads = listOf(worktreeThread),
            hasLoaded = true,
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onNodeWithText("project-feature").assertIsDisplayed()
  }

  @Test
  fun worktreeNodeNotShownWhenNoWorktrees() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
  }

  @Test
  fun worktreeNodeShowsBranchLabel() {
    val now = 1_700_000_000_000L
    val worktreeThread = AgentSessionThread(id = "wt-thread-1", title = "WT Thread", updatedAt = now, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
            threads = listOf(worktreeThread),
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("project-feature").assertIsDisplayed()
    composeRule.onNodeWithText("[feature-x]").assertIsDisplayed()
  }

  @Test
  fun projectNodeShowsBranchLabelWhenWorktreesExist() {
    val now = 1_700_000_000_000L
    val worktreeThread = AgentSessionThread(id = "wt-thread-1", title = "WT Thread", updatedAt = now, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        branch = "main",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
            threads = listOf(worktreeThread),
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("Project A").assertIsDisplayed()
    composeRule.onNodeWithText("[main]").assertIsDisplayed()
  }

  @Test
  fun worktreeNodeShowsDetachedLabelWhenNoBranch() {
    val now = 1_700_000_000_000L
    val worktreeThread = AgentSessionThread(id = "wt-thread-1", title = "WT Thread", updatedAt = now, archived = false)
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-detached",
            name = "project-detached",
            branch = null,
            isOpen = false,
            hasLoaded = true,
            threads = listOf(worktreeThread),
          ),
        ),
      ),
    )

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("project-detached").assertIsDisplayed()
    composeRule.onAllNodesWithText("[${AgentSessionsBundle.message("toolwindow.worktree.detached")}]")
      .assertCountEquals(2)
  }

  @Test
  fun clickingWorktreeSubAgentUsesWorktreePath() {
    val now = 1_700_000_000_000L
    val subAgent = AgentSubAgent(id = "sub-agent-1", name = "Sub Agent")
    val worktreeThread = AgentSessionThread(
      id = "wt-thread-1",
      title = "WT Thread",
      updatedAt = now,
      archived = false,
      subAgents = listOf(subAgent),
    )
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        branch = "main",
        isOpen = true,
        hasLoaded = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature-x",
            isOpen = false,
            hasLoaded = true,
            threads = listOf(worktreeThread),
          ),
        ),
      ),
    )

    var openedPath: String? = null
    var openedSubAgentId: String? = null

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        onOpenSubAgent = { path, _, selectedSubAgent ->
          openedPath = path
          openedSubAgentId = selectedSubAgent.id
        },
        nowProvider = { now },
      )
    }

    composeRule.onNodeWithText("project-feature").performMouseInput { doubleClick() }
    composeRule.onNodeWithText("WT Thread").performMouseInput { doubleClick() }
    composeRule.onNodeWithText("Sub Agent").performClick()

    assertThat(openedPath).isEqualTo("/work/project-feature")
    assertThat(openedSubAgentId).isEqualTo("sub-agent-1")
  }

  @Test
  fun moreProjectsRowNotShownWhenWithinLimit() {
    val projects = (1..5).map { i ->
      AgentProjectSessions(
        path = "/work/project-$i",
        name = "Project $i",
        isOpen = false,
      )
    }

    composeRule.setContentWithTheme {
      agentSessionsToolWindowContent(
        state = AgentSessionsState(projects = projects),
        onRefresh = {},
        onOpenProject = {},
        visibleClosedProjectCount = 10,
      )
    }

    composeRule.onNodeWithText("Project 1").assertIsDisplayed()
    composeRule.onNodeWithText("Project 5").assertIsDisplayed()
    composeRule.onAllNodesWithText("More", substring = true).assertCountEquals(0)
  }
}

private fun providerUnavailableMessage(provider: AgentSessionProvider): String {
  val providerLabel = providerDisplayName(provider)
  return AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", providerLabel)
}
