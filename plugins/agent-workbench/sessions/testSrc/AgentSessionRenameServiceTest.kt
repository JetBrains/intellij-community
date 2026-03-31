// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameMode
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionRenameServiceTest {
  @Test
  fun renameThreadRefreshesScopedProviderOnSuccess(): Unit = runBlocking(Dispatchers.Default) {
    val refreshedPaths = mutableListOf<Pair<String, AgentSessionProvider>>()
    var renamedPath: String? = null
    var renamedThreadId: String? = null
    var renamedName: String? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsRenameThread = true,
      renameThreadHandler = { path, threadId, name ->
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
      refreshProviderForPath = { path, provider -> refreshedPaths += path to provider },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
      )

      val job = service.renameThreadFromTree(target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      assertThat(renamedPath).isEqualTo("/work/project")
      assertThat(renamedThreadId).isEqualTo("thread-1")
      assertThat(renamedName).isEqualTo("Renamed thread")
      assertThat(refreshedPaths).containsExactly("/work/project" to AgentSessionProvider.CODEX)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadSkipsBlankAndUnchangedRequests(): Unit = runBlocking(Dispatchers.Default) {
    var renameCalls = 0
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsRenameThread = true,
      renameThreadHandler = { _, _, _ ->
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
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
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
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadNotifiesOnFailureAndSkipsRefresh(): Unit = runBlocking(Dispatchers.Default) {
    var failureNotifications = 0
    var refreshCalls = 0
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsRenameThread = true,
      renameThreadHandler = { _, _, _ -> false },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      notifyRenameFailure = { failureNotifications += 1 },
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
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadDispatchesToActiveEditorTabWithoutRefresh(): Unit = runBlocking(Dispatchers.Default) {
    var refreshCalls = 0
    var dispatchedContext: AgentChatEditorTabActionContext? = null
    var dispatchedTarget: SessionActionTarget.Thread? = null
    var dispatchedPlan: AgentInitialMessageDispatchPlan? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      renameThreadModes = mapOf(AgentThreadRenameContext.EDITOR_TAB to AgentThreadRenameMode.ACTIVE_EDITOR_DISPATCH),
      renameThreadDispatchStepsBuilder = { name -> listOf(AgentInitialMessageDispatchStep(text = "/rename $name")) },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { context, target, plan ->
        dispatchedContext = context
        dispatchedTarget = target
        dispatchedPlan = plan
      },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
        title = "Original title",
      )
      val context = AgentChatEditorTabActionContext(
        project = ProjectManager.getInstance().defaultProject,
        path = "/work/project",
        tabKey = "claude:thread-1",
        threadIdentity = "claude:thread-1",
        threadCoordinates = AgentChatThreadCoordinates(
          provider = AgentSessionProvider.CLAUDE,
          sessionId = "thread-1",
          isPending = false,
        ),
        sessionActionTarget = target,
      )

      val job = service.renameThreadFromEditorTab(context, target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      val renamePlan = checkNotNull(dispatchedPlan)
      assertThat(refreshCalls).isZero()
      assertThat(dispatchedContext).isEqualTo(context)
      assertThat(dispatchedTarget).isEqualTo(target)
      assertThat(renamePlan.postStartDispatchSteps.map { it.text }).containsExactly("/rename Renamed thread")
      assertThat(renamePlan.initialMessageToken).startsWith("rename:claude:thread-1:")
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
      renameThreadModes = mapOf(AgentThreadRenameContext.EDITOR_TAB to AgentThreadRenameMode.ACTIVE_EDITOR_DISPATCH),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> },
      notifyRenameFailure = {},
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
        title = "Original title",
      )
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
