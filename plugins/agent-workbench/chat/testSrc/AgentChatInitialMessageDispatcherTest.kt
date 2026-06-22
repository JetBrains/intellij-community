// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatInitialMessageDispatcherTest {
  @Test
  fun juniePlanModeEnsureSendsBackTabAfterPromptInputIsReady(): Unit = timeoutRunBlocking {
    val file = createFile(
      provider = AgentSessionProvider.JUNIE,
      steps = listOf(
        terminalPlanModeStep(),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      recentOutputTail = "Type your prompt",
      outputObservations = listOf(AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Plan Mode")),
    )

    createDispatcher(file, AgentSessionProvider.JUNIE).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("backtab", "text:Refactor this")
  }

  @Test
  fun juniePlanModeEnsureSkipsBackTabWhenPlanModeAlreadyVisible(): Unit = timeoutRunBlocking {
    val file = createFile(
      provider = AgentSessionProvider.JUNIE,
      steps = listOf(
        terminalPlanModeStep(),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      recentOutputTail = "Plan Mode active. Type your prompt",
    )

    createDispatcher(file, AgentSessionProvider.JUNIE).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("text:Refactor this")
  }

  @Test
  fun planModePreSendRetriesDoNotConsumePostSendConfirmationBudget(): Unit = timeoutRunBlocking {
    val behavior = DelayedPlanModeBehavior(preSendRetryCount = 6)
    val file = createFile(
      provider = AgentSessionProvider.JUNIE,
      steps = listOf(
        terminalPlanModeStep(),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Plan Mode")),
    )

    createDispatcher(file, behavior = behavior).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("backtab", "text:Refactor this")
    assertThat(behavior.afterSendRetryAttempts).containsExactly(0)
  }

  @Test
  fun transientBusyRetriesDoNotConsumePostSendConfirmationBudget(): Unit = timeoutRunBlocking {
    val behavior = TransientBusyPlanModeBehavior(transientBusyRetryCount = 2)
    val file = createFile(
      provider = AgentSessionProvider.JUNIE,
      steps = listOf(
        terminalPlanModeStep(),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Working"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Working"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Plan Mode"),
      ),
    )

    createDispatcher(file, behavior = behavior).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("backtab", "backtab", "backtab", "text:Refactor this")
    assertThat(behavior.afterSendRetryAttempts).containsExactly(0, 0, 0)
  }

  @Test
  fun persistedPromptDataIsNotRestoredForDispatch() {
    val identity = AgentChatTabIdentity(
      projectHash = "hash",
      projectPath = "/project",
      threadIdentity = "junie:new-test",
      subAgentId = null,
    )
    val tabKey = AgentChatTabKey.fromIdentity(identity)
    val service = AgentChatTabsStateService(scope = null)
    service.loadState(
      AgentChatTabsState(
        tabsByKey = mapOf(
          tabKey.value to PersistedAgentChatTabState(
            projectHash = identity.projectHash,
            projectPath = identity.projectPath,
            threadIdentity = identity.threadIdentity,
            subAgentId = identity.subAgentId,
            threadId = "new-test",
            lastKnownTitle = "Junie",
            updatedAt = System.currentTimeMillis(),
          )
        ),
      )
    )

    val loaded = checkNotNull(service.load(tabKey))

    assertThat(loaded.runtime.initialPromptRecord).isNull()
    assertThat(loaded.runtime.terminalPromptDispatch).isNull()
    assertThat(loaded.runtime.initialMessageDispatchSteps).isEmpty()
    assertThat(loaded.runtime.initialMessageToken).isNull()
    assertThat(loaded.runtime.initialMessageSent).isFalse()
  }

  @Test
  fun stoppedPlanModeDispatchReportsPromptNotSent(): Unit = timeoutRunBlocking {
    val file = createFile(
      provider = AgentSessionProvider.JUNIE,
      steps = listOf(
        terminalPlanModeStep(),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Default mode")),
    )
    val reportedFiles = mutableListOf<AgentChatVirtualFile>()

    createDispatcher(
      file = file,
      behavior = StopPlanModeBehavior,
      planModeInitialPromptStopReporter = { _, stoppedFile -> reportedFiles += stoppedFile },
    ).schedule(tab)

    waitForCondition { file.initialMessageDispatchSteps.isEmpty() }
    assertThat(tab.events).containsExactly("backtab")
    assertThat(file.initialMessageSent).isFalse()
    assertThat(reportedFiles).containsExactly(file)
  }
}

private class DelayedPlanModeBehavior(
  private val preSendRetryCount: Int,
) : AgentChatProviderBehavior {
  val afterSendRetryAttempts: MutableList<Int> = mutableListOf()
  private var beforeSendCalls: Int = 0

  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (beforeSendCalls < preSendRetryCount) {
      beforeSendCalls++
      return AgentChatInitialMessageRetryDecision.RetryWithoutReadiness(backoffMs = 1)
    }
    beforeSendCalls++
    return AgentChatInitialMessageRetryDecision.PROCEED
  }

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    afterSendRetryAttempts += retryAttempt
    return if (retryAttempt == 0) {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
    else {
      AgentChatInitialMessageRetryDecision.Stop
    }
  }
}

private class TransientBusyPlanModeBehavior(
  private val transientBusyRetryCount: Int,
) : AgentChatProviderBehavior {
  val afterSendRetryAttempts: MutableList<Int> = mutableListOf()
  private var afterSendCalls: Int = 0

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    afterSendRetryAttempts += retryAttempt
    if (afterSendCalls < transientBusyRetryCount) {
      afterSendCalls++
      return AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness(backoffMs = 1)
    }
    afterSendCalls++
    return AgentChatInitialMessageRetryDecision.PROCEED
  }
}

private fun createDispatcher(
  file: AgentChatVirtualFile,
  provider: AgentSessionProvider = AgentSessionProvider.JUNIE,
  behavior: AgentChatProviderBehavior = resolveAgentChatProviderBehavior(provider),
  planModeInitialPromptStopReporter: (Project, AgentChatVirtualFile) -> Unit = { _, _ -> },
): AgentChatInitialMessageDispatcher {
  return AgentChatInitialMessageDispatcher(
    project = ProjectManager.getInstance().defaultProject,
    file = file,
    behavior = behavior,
    tabSnapshotWriter = AgentChatTabSnapshotWriter {},
    planModeInitialPromptStopReporter = planModeInitialPromptStopReporter,
  )
}

private object StopPlanModeBehavior : AgentChatProviderBehavior {
  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    return if (dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE) {
      AgentChatInitialMessageRetryDecision.Stop
    }
    else {
      AgentChatInitialMessageRetryDecision.PROCEED
    }
  }
}

private fun createFile(
  steps: List<AgentInitialMessageDispatchStep>,
  provider: AgentSessionProvider = AgentSessionProvider.JUNIE,
): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = "/project",
    threadIdentity = "${provider.value}:new-test",
    shellCommand = emptyList(),
    threadId = "new-test",
    threadTitle = provider.value,
    subAgentId = null,
  ).also { file ->
    file.updateInitialMessageMetadata(
      initialMessageDispatchSteps = steps,
      initialMessageDispatchStepIndex = 0,
      initialMessageToken = "token",
      initialMessageSent = false,
    )
  }
}

private fun terminalPlanModeStep(
  completionPolicy: AgentInitialMessageDispatchCompletionPolicy = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE,
): AgentInitialMessageDispatchStep {
  return AgentInitialMessageDispatchStep(
    action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
    timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
    completionPolicy = completionPolicy,
  )
}

private class FakeTerminalTab(
  override val coroutineScope: CoroutineScope,
  private val recentOutputTail: String = "",
  outputObservations: List<AgentChatTerminalOutputObservation> = emptyList(),
) : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = component
  override val sessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.Running)
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()
  override val terminalView: TerminalView? = null
  val events: MutableList<String> = mutableListOf()
  private val observations = ArrayDeque(outputObservations)

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    return AgentChatTerminalOutputCheckpoint(regularEndOffset = 0, alternativeEndOffset = 0)
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation {
    return if (observations.isEmpty()) {
      AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.TIMEOUT, "")
    }
    else {
      observations.removeFirst()
    }
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness = AgentChatTerminalInputReadiness.READY

  override suspend fun readRecentOutputTail(): String = recentOutputTail

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    events.add("text:$text")
  }

  override fun sendBackTab(): Boolean {
    events.add("backtab")
    return true
  }
}

private suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
