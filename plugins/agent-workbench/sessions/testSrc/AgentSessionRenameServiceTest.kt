// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.service.AgentSessionThreadActivityPresentationUpdate
import com.intellij.agent.workbench.sessions.service.AgentSessionThreadPresentationUpdater
import com.intellij.agent.workbench.sessions.state.InMemoryAgentSessionThreadTitleOverrides
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
    val presentationUpdater = RecordingThreadPresentationUpdater { operationOrder += "presentation" }
    val titleOverrides = InMemoryAgentSessionThreadTitleOverrides()
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
      titleOverrides = titleOverrides,
      notifyRenameFailure = { error("rename failure notification should not be shown") },
      threadPresentationUpdater = presentationUpdater,
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
        thread = threadModel(AgentSessionProvider.CODEX, "thread-1", "Original title")
          .copy(activity = AgentThreadActivity.PROCESSING),
      )

      val job = service.renameThreadFromTree(target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      assertThat(renamedPath).isEqualTo("/work/project")
      assertThat(renamedThreadId).isEqualTo("thread-1")
      assertThat(renamedName).isEqualTo("Renamed thread")
      assertThat(titleOverrides.getTitle("/work/project", AgentSessionProvider.CODEX, "thread-1")).isEqualTo("Renamed thread")
      assertThat(presentationUpdater.threadUpdates).containsExactly(
        ThreadPresentationUpdate(
          provider = AgentSessionProvider.CODEX,
          path = "/work/project",
          threadId = "thread-1",
          title = "Renamed thread",
          activity = AgentThreadActivity.PROCESSING,
        )
      )
      assertThat(operationOrder).containsExactly("presentation", "refresh")
      assertThat(refreshedPaths).containsExactly("/work/project" to AgentSessionProvider.CODEX)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadSkipsBlankAndUnchangedRequests(): Unit = runBlocking(Dispatchers.Default) {
    var renameCalls = 0
    val presentationUpdater = RecordingThreadPresentationUpdater()
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
      threadPresentationUpdater = presentationUpdater,
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
      assertThat(presentationUpdater.threadUpdates).isEmpty()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadNotifiesOnFailureAndSkipsRefresh(): Unit = runBlocking(Dispatchers.Default) {
    var failureNotifications = 0
    var refreshCalls = 0
    val titleOverrides = InMemoryAgentSessionThreadTitleOverrides()
    val presentationUpdater = RecordingThreadPresentationUpdater()
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
      titleOverrides = titleOverrides,
      notifyRenameFailure = { failureNotifications += 1 },
      threadPresentationUpdater = presentationUpdater,
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
      assertThat(presentationUpdater.threadUpdates).isEmpty()
      assertThat(titleOverrides.getTitle("/work/project", AgentSessionProvider.CODEX, "thread-1")).isNull()
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

private class RecordingThreadPresentationUpdater(
  private val beforeThreadUpdate: () -> Unit = {},
) : AgentSessionThreadPresentationUpdater {
  val threadUpdates = mutableListOf<ThreadPresentationUpdate>()

  override suspend fun updateThread(
    provider: AgentSessionProvider,
    path: String,
    threadId: String,
    title: String,
    activity: AgentThreadActivity?,
  ): Int {
    beforeThreadUpdate()
    threadUpdates += ThreadPresentationUpdate(
      provider = provider,
      path = path,
      threadId = threadId,
      title = title,
      activity = activity,
    )
    return 1
  }

  override suspend fun updateProviderSnapshot(
    provider: AgentSessionProvider,
    authoritativePaths: Set<String>,
    threadsByPath: Map<String, List<AgentSessionThread>>,
  ): Int {
    error("provider snapshot presentation update should not be used")
  }

  override suspend fun updateActivityHints(
    provider: AgentSessionProvider,
    updates: Collection<AgentSessionThreadActivityPresentationUpdate>,
  ): Int {
    error("activity hint presentation update should not be used")
  }
}

private data class ThreadPresentationUpdate(
  val provider: AgentSessionProvider,
  @JvmField val path: String,
  @JvmField val threadId: String,
  @JvmField val title: String,
  @JvmField val activity: AgentThreadActivity?,
)
