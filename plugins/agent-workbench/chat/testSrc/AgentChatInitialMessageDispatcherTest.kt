// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
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
  fun codexPlanModeEnsureSendsBackTabBeforePrompt(): Unit = timeoutRunBlocking {
    val file = createFile(
      listOf(
        terminalPlanModeStep(
          completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
        ),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Plan mode")),
    )

    createDispatcher(file).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("backtab", "text:Refactor this")
  }

  @Test
  fun codexPlanModeEnsureSkipsBackTabWhenPlanModeAlreadyVisible(): Unit = timeoutRunBlocking {
    val file = createFile(
      listOf(
        terminalPlanModeStep(
          completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
        ),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      recentOutputTail = "gpt-5.3-codex medium Plan mode (shift+tab to cycle)",
    )

    createDispatcher(file).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("text:Refactor this")
  }

  @Test
  fun codexPlanModeEnsureFailureFallsBackToPromptSubmission(): Unit = timeoutRunBlocking {
    val file = createFile(
      listOf(
        terminalPlanModeStep(
          completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
        ),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val snapshots = mutableListOf<AgentChatTabSnapshot>()
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Default mode"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Default mode"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Default mode"),
      ),
    )

    AgentChatInitialMessageDispatcher(
      file = file,
      behavior = resolveAgentChatProviderBehavior(AgentSessionProvider.CODEX),
      tabSnapshotWriter = AgentChatTabSnapshotWriter { snapshot -> snapshots.add(snapshot) },
    ).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("backtab", "backtab", "backtab", "text:Refactor this")
    assertThat(file.initialMessageDispatchSteps).hasSize(2)
    assertThat(file.initialMessageDispatchStepIndex).isEqualTo(2)
    assertThat(file.initialMessageToken).isEqualTo("token")
    assertThat(file.initialMessageSent).isTrue()
    assertThat(snapshots).hasSize(2)
    assertThat(snapshots.last().runtime.initialMessageDispatchSteps).hasSize(2)
    assertThat(snapshots.last().runtime.initialMessageDispatchStepIndex).isEqualTo(2)
    assertThat(snapshots.last().runtime.initialMessageToken).isEqualTo("token")
    assertThat(snapshots.last().runtime.initialMessageSent).isTrue()
  }

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
  fun legacyCodexPlanModeActionRestoresAsTerminalPlanModeAction() {
    val identity = AgentChatTabIdentity(
      projectHash = "hash",
      projectPath = "/project",
      threadIdentity = "codex:new-test",
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
            lastKnownTitle = "Codex",
            initialMessageDispatchSteps = listOf(
              PersistedAgentChatInitialMessageDispatchStep(
                action = "ENSURE_CODEX_PLAN_MODE",
                timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS.name,
              )
            ),
            updatedAt = System.currentTimeMillis(),
          )
        ),
      )
    )

    val loaded = checkNotNull(service.load(tabKey))

    assertThat(loaded.runtime.initialMessageDispatchSteps.single().action)
      .isEqualTo(AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE)
  }
}

private fun createDispatcher(
  file: AgentChatVirtualFile,
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
): AgentChatInitialMessageDispatcher {
  return AgentChatInitialMessageDispatcher(
    file = file,
    behavior = resolveAgentChatProviderBehavior(provider),
    tabSnapshotWriter = AgentChatTabSnapshotWriter {},
  )
}

private fun createFile(
  steps: List<AgentInitialMessageDispatchStep>,
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
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
