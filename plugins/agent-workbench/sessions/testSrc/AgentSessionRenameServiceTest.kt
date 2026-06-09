// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRenameServiceTest {
  @Test
  fun renameThreadRefreshesScopedProviderOnSuccess(): Unit = runBlocking(Dispatchers.Default) {
    val refreshedPaths = mutableListOf<Pair<String, AgentSessionProvider>>()
    val operationOrder = mutableListOf<String>()
    val stateStore = AgentSessionsStateStore()
    val presentationModel = AgentSessionThreadPresentationModel()
    var renamedPath: String? = null
    var renamedThreadId: String? = null
    var renamedName: String? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameActionOverride = { path, threadId, name ->
        renamedPath = path
        renamedThreadId = threadId
        renamedName = name
        true
      },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { path, provider ->
        operationOrder += "refresh"
        refreshedPaths += path to provider
      },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
      stateStore = stateStore,
      presentationModel = presentationModel,
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
        thread = threadModel(AgentSessionProvider.CODEX, "thread-1", "Original title")
          .copy(activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING)),
      )
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project",
            name = "Project",
            isOpen = true,
            threads = listOf(checkNotNull(target.thread)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      val job = service.renameThreadFromTree(target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      assertThat(renamedPath).isEqualTo("/work/project")
      assertThat(renamedThreadId).isEqualTo("thread-1")
      assertThat(renamedName).isEqualTo("Renamed thread")
      assertThat(stateStore.snapshot().projects.single().threads.single().title).isEqualTo("Renamed thread")
      val presentation = presentationModel.snapshot()[presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")]
      assertThat(presentation?.title).isEqualTo("Renamed thread")
      assertThat(presentation?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(operationOrder).containsExactly("refresh")
      assertThat(refreshedPaths).containsExactly("/work/project" to AgentSessionProvider.CODEX)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadSkipsBlankAndUnchangedRequests(): Unit = runBlocking(Dispatchers.Default) {
    var renameCalls = 0
    val presentationModel = AgentSessionThreadPresentationModel()
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameActionOverride = { _, _, _ ->
        renameCalls += 1
        true
      },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> error("refresh should not be called for skipped rename") },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
      presentationModel = presentationModel,
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
      )

      assertThat(service.renameThreadFromTree(target, "   ")).isNull()
      assertThat(service.renameThreadFromTree(target, " Original\n  title ")).isNull()
      assertThat(renameCalls).isZero()
      assertThat(presentationModel.snapshot()).isEmpty()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadNotifiesOnFailureAndSkipsRefresh(): Unit = runBlocking(Dispatchers.Default) {
    var failureNotifications = 0
    var refreshCalls = 0
    val presentationModel = AgentSessionThreadPresentationModel()
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameActionOverride = { _, _, _ -> false },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      notifyRenameFailure = { failureNotifications += 1 },
      presentationModel = presentationModel,
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
      )

      val job = service.renameThreadFromTree(target, "Renamed thread")
      joinAll(checkNotNull(job))

      assertThat(failureNotifications).isEqualTo(1)
      assertThat(refreshCalls).isZero()
      assertThat(presentationModel.snapshot()).isEmpty()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun canRenameThreadInTreeRequiresConcreteRenameActionAndRejectsPendingNewThreadIds() {
    val renameDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameActionOverride = { _, _, _ -> true },
    )
    val noRenameDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> error("refresh should not be called while checking rename availability") },
      findProviderDescriptor = { provider ->
        when (provider) {
          AgentSessionProvider.CODEX -> renameDescriptor
          AgentSessionProvider.CLAUDE -> noRenameDescriptor
          else -> null
        }
      },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      assertThat(service.canRenameThreadInTree(threadTarget(AgentSessionProvider.CODEX, "thread-1"))).isTrue()
      assertThat(service.canRenameThreadInTree(threadTarget(AgentSessionProvider.CLAUDE, "thread-1"))).isFalse()
      val pendingTarget = threadTarget(AgentSessionProvider.CODEX, "new-codex-pending")
      assertThat(service.canRenameThreadInTree(pendingTarget)).isFalse()
      assertThat(service.renameThreadFromTree(pendingTarget, "Renamed")).isNull()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun canRenameThreadInEditorTabRequiresMatchingConcreteThread() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameActionOverride = { _, _, _ -> true },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      notifyRenameFailure = {},
    )

    try {
      val target = threadTarget(AgentSessionProvider.CLAUDE, "thread-1")
      val matchedContext = AgentChatEditorTabActionContext(
        project = ProjectManager.getInstance().defaultProject,
        path = "/work/project",
        tabKey = "claude:thread-1",
        threadIdentity = "claude:thread-1",
        threadCoordinates = AgentChatThreadCoordinates(
          provider = AgentSessionProvider.CLAUDE,
          sessionId = "thread-1",
          isPending = false,
        ),
      )
      val pendingContext = matchedContext.copy(
        threadCoordinates = matchedContext.threadCoordinates?.copy(isPending = true),
      )
      val mismatchedContext = matchedContext.copy(
        threadCoordinates = matchedContext.threadCoordinates?.copy(sessionId = "thread-2"),
      )

      assertThat(service.canRenameThreadInEditorTab(matchedContext, target)).isTrue()
      assertThat(service.canRenameThreadInEditorTab(pendingContext, target)).isFalse()
      assertThat(service.canRenameThreadInEditorTab(mismatchedContext, target)).isFalse()
    }
    finally {
      scope.cancel()
    }
  }
}

private fun threadTarget(provider: AgentSessionProvider, threadId: String): SessionActionTarget.Thread {
  return SessionActionTarget.Thread(
    path = "/work/project",
    provider = provider,
    threadId = threadId,
    title = "Original title",
  )
}

private fun threadModel(provider: AgentSessionProvider, id: String, title: String): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = 1L,
    archived = false,
    provider = provider,
  )
}

private fun presentationKey(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
): AgentSessionThreadPresentationKey {
  return checkNotNull(AgentSessionThreadPresentationKey.create(path, provider, threadId))
}
