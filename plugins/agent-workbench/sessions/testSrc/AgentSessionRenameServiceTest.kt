// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameHandler
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.state.InMemoryAgentSessionThreadTitleOverrides
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
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
    val presentationUpdates = mutableListOf<OpenTabPresentationUpdate>()
    val titleOverrides = InMemoryAgentSessionThreadTitleOverrides()
    var renamedPath: String? = null
    var renamedThreadId: String? = null
    var renamedName: String? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = backendRenameHandler { path, threadId, name ->
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
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used") },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
      openAgentChatTabPresentationUpdater = { provider, refreshedPaths, titleMap, activityMap ->
        operationOrder += "presentation"
        presentationUpdates += OpenTabPresentationUpdate(
          provider = provider,
          refreshedPaths = refreshedPaths,
          titleMap = titleMap,
          activityMap = activityMap,
        )
        titleMap.size
      },
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

      val job = service.renameThreadFromTree(ProjectManager.getInstance().defaultProject, target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      assertThat(renamedPath).isEqualTo("/work/project")
      assertThat(renamedThreadId).isEqualTo("thread-1")
      assertThat(renamedName).isEqualTo("Renamed thread")
      assertThat(titleOverrides.getTitle("/work/project", AgentSessionProvider.CODEX, "thread-1")).isEqualTo("Renamed thread")
      val expectedKey = "/work/project" to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1")
      val presentationUpdate = presentationUpdates.single()
      assertThat(presentationUpdate.provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(presentationUpdate.refreshedPaths).isEmpty()
      assertThat(presentationUpdate.titleMap).containsExactlyEntriesOf(mapOf(expectedKey to "Renamed thread"))
      assertThat(presentationUpdate.activityMap).containsExactlyEntriesOf(mapOf(expectedKey to AgentThreadActivity.PROCESSING))
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
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = backendRenameHandler { _, _, _ ->
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
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used") },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
      )

      val project = ProjectManager.getInstance().defaultProject
      assertThat(service.renameThreadFromTree(project, target, "   ")).isNull()
      assertThat(service.renameThreadFromTree(project, target, " Original\n  title ")).isNull()
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
    var presentationUpdateCalls = 0
    val titleOverrides = InMemoryAgentSessionThreadTitleOverrides()
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = backendRenameHandler { _, _, _ -> false },
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      titleOverrides = titleOverrides,
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used") },
      notifyRenameFailure = { failureNotifications += 1 },
      openAgentChatTabPresentationUpdater = { _, _, _, _ ->
        presentationUpdateCalls += 1
        0
      },
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
        title = "Original title",
      )

      val job = service.renameThreadFromTree(ProjectManager.getInstance().defaultProject, target, "Renamed thread")
      joinAll(checkNotNull(job))

      assertThat(failureNotifications).isEqualTo(1)
      assertThat(refreshCalls).isZero()
      assertThat(presentationUpdateCalls).isZero()
      assertThat(titleOverrides.getTitle("/work/project", AgentSessionProvider.CODEX, "thread-1")).isNull()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadDispatchesToActiveEditorTabAndRefreshes(): Unit = runBlocking(Dispatchers.Default) {
    var refreshCalls = 0
    val titleOverrides = InMemoryAgentSessionThreadTitleOverrides()
    var dispatchedContext: AgentChatEditorTabActionContext? = null
    var dispatchedTarget: SessionActionTarget.Thread? = null
    var dispatchedPlan: AgentInitialMessageDispatchPlan? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = dispatchRenameHandler(
        AgentThreadRenameContext.TREE_POPUP,
        AgentThreadRenameContext.EDITOR_TAB,
      ),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      titleOverrides = titleOverrides,
      dispatchRenameInEditorTab = { context, target, plan ->
        dispatchedContext = context
        dispatchedTarget = target
        dispatchedPlan = plan
      },
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used") },
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
      assertThat(refreshCalls).isEqualTo(1)
      assertThat(dispatchedContext).isEqualTo(context)
      assertThat(dispatchedTarget).isEqualTo(target)
      assertThat(renamePlan.postStartDispatchSteps.map { it.text }).containsExactly("/rename Renamed thread")
      assertThat(renamePlan.initialMessageToken).startsWith("rename:claude:thread-1:")
      assertThat(titleOverrides.getTitle("/work/project", AgentSessionProvider.CLAUDE, "thread-1")).isNull()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun canRenameThreadInTreeSupportsDispatchHandlersWithConcreteThreadModel() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = dispatchRenameHandler(
        AgentThreadRenameContext.TREE_POPUP,
        AgentThreadRenameContext.EDITOR_TAB,
      ),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> },
      dispatchRenameFromTree = { _, _, _ -> },
      notifyRenameFailure = {},
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
        title = "Original title",
        thread = threadModel(provider = AgentSessionProvider.CLAUDE, id = "thread-1", title = "Original title"),
      )

      assertThat(service.canRenameThreadInTree(target)).isTrue()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun canRenameThreadInTreeRejectsPendingNewThreadIds() {
    val backendDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = backendRenameHandler { _, _, _ -> true },
    )
    val dispatchDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = dispatchRenameHandler(AgentThreadRenameContext.TREE_POPUP),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> error("refresh should not be called for pending thread rename") },
      findProviderDescriptor = { provider ->
        when (provider) {
          AgentSessionProvider.CODEX -> backendDescriptor
          AgentSessionProvider.CLAUDE -> dispatchDescriptor
          else -> null
        }
      },
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used") },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      val backendTarget = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CODEX,
        threadId = "new-codex-pending",
        title = "New Thread",
      )
      val dispatchTarget = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "new-claude-pending",
        title = "New Thread",
        thread = threadModel(provider = AgentSessionProvider.CLAUDE, id = "new-claude-pending", title = "New Thread"),
      )

      assertThat(service.canRenameThreadInTree(backendTarget)).isFalse()
      assertThat(service.canRenameThreadInTree(dispatchTarget)).isFalse()
      assertThat(service.renameThreadFromTree(ProjectManager.getInstance().defaultProject, backendTarget, "Renamed")).isNull()
      assertThat(service.renameThreadFromTree(ProjectManager.getInstance().defaultProject, dispatchTarget, "Renamed")).isNull()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadDispatchesFromTreeAndRefreshes(): Unit = runBlocking(Dispatchers.Default) {
    var refreshCalls = 0
    var dispatchedProject = ProjectManager.getInstance().defaultProject
    var dispatchedTarget: SessionActionTarget.Thread? = null
    var dispatchedPlan: AgentInitialMessageDispatchPlan? = null
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = dispatchRenameHandler(
        AgentThreadRenameContext.TREE_POPUP,
        AgentThreadRenameContext.EDITOR_TAB,
      ),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      dispatchRenameFromTree = { project, target, plan ->
        dispatchedProject = project
        dispatchedTarget = target
        dispatchedPlan = plan
      },
      notifyRenameFailure = { error("rename failure notification should not be shown") },
    )

    try {
      val thread = threadModel(provider = AgentSessionProvider.CLAUDE, id = "thread-1", title = "Original title")
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
        title = "Original title",
        thread = thread,
      )
      val project = ProjectManager.getInstance().defaultProject

      val job = service.renameThreadFromTree(project, target, "  Renamed\n\n  thread  ")
      joinAll(checkNotNull(job))

      val renamePlan = checkNotNull(dispatchedPlan)
      assertThat(refreshCalls).isEqualTo(1)
      assertThat(dispatchedProject).isEqualTo(project)
      assertThat(dispatchedTarget).isEqualTo(target)
      assertThat(renamePlan.postStartDispatchSteps.map { it.text }).containsExactly("/rename Renamed thread")
      assertThat(renamePlan.initialMessageToken).startsWith("rename:claude:thread-1:")
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun renameThreadFromTreeNotifiesFailureWhenDispatchThreadModelIsMissing(): Unit = runBlocking(Dispatchers.Default) {
    var failureNotifications = 0
    var refreshCalls = 0
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      threadRenameHandlerOverride = dispatchRenameHandler(AgentThreadRenameContext.TREE_POPUP),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> refreshCalls += 1 },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> error("editor rename dispatch should not be used") },
      dispatchRenameFromTree = { _, _, _ -> error("tree rename dispatch should not be used when thread model is missing") },
      notifyRenameFailure = { failureNotifications += 1 },
    )

    try {
      val target = SessionActionTarget.Thread(
        path = "/work/project",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "thread-1",
        title = "Original title",
      )

      val job = service.renameThreadFromTree(ProjectManager.getInstance().defaultProject, target, "Renamed thread")
      joinAll(checkNotNull(job))

      assertThat(failureNotifications).isEqualTo(1)
      assertThat(refreshCalls).isZero()
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
      threadRenameHandlerOverride = dispatchRenameHandler(AgentThreadRenameContext.EDITOR_TAB),
    )

    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service = AgentSessionRenameService(
      serviceScope = scope,
      refreshProviderForPath = { _, _ -> },
      findProviderDescriptor = { provider -> descriptor.takeIf { it.provider == provider } },
      dispatchRenameInEditorTab = { _, _, _ -> },
      dispatchRenameFromTree = { _, _, _ -> },
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

private fun backendRenameHandler(renameThread: suspend (String, String, String) -> Boolean): AgentThreadRenameHandler.Backend {
  return AgentThreadRenameHandler.backend(action = renameThread)
}

private fun dispatchRenameHandler(vararg renameContexts: AgentThreadRenameContext): AgentThreadRenameHandler.ChatDispatch {
  return object : AgentThreadRenameHandler.ChatDispatch {
    override val supportedContexts: Set<AgentThreadRenameContext>
      get() = renameContexts.toSet()

    override fun buildDispatchPlan(normalizedName: String): AgentInitialMessageDispatchPlan {
      return AgentInitialMessageDispatchPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "/rename $normalizedName")),
      )
    }
  }
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

private data class OpenTabPresentationUpdate(
  val provider: AgentSessionProvider,
  @JvmField val refreshedPaths: Set<String>,
  @JvmField val titleMap: Map<Pair<String, String>, String>,
  @JvmField val activityMap: Map<Pair<String, String>, AgentThreadActivity>,
)
