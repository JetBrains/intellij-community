// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageProviderDispatchRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentTerminalPromptDispatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
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
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatInitialMessageDispatcherTest {
  @Test
  fun preSendRetriesDoNotConsumePostSendConfirmationBudget(): Unit = timeoutRunBlocking {
    val behavior = DelayedObservedDispatchBehavior(preSendRetryCount = 6)
    val file = createFile(
      steps = listOf(
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
    assertThat(tab.events).containsExactly("text:Refactor this")
    assertThat(behavior.afterSendRetryAttempts).containsExactly(0)
  }

  @Test
  fun transientBusyRetriesDoNotConsumePostSendConfirmationBudget(): Unit = timeoutRunBlocking {
    val behavior = TransientBusyObservedDispatchBehavior(transientBusyRetryCount = 2)
    val file = createFile(
      steps = listOf(
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
    assertThat(tab.events).containsExactly("text:Refactor this", "text:Refactor this", "text:Refactor this")
    assertThat(behavior.afterSendRetryAttempts).containsExactly(0, 0, 0)
  }

  @Test
  fun postSendObservationWaitsWithoutResendingDispatch(): Unit = timeoutRunBlocking {
    val behavior = AwaitMoreObservedDispatchBehavior(awaitMoreCount = 2)
    val file = createFile(
      steps = listOf(
        AgentInitialMessageDispatchStep(
          text = "Plan this refactor",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      outputObservations = listOf(
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Running SessionStart hook"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "still starting"),
        AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "Plan Mode"),
      ),
    )

    createDispatcher(file, behavior = behavior).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("text:Plan this refactor")
    assertThat(behavior.afterSendRetryAttempts).containsExactly(0, 1, 2)
  }

  @Test
  fun providerDispatchWaitsForTerminalTitleThreadIdBeforeSendingToProvider(): Unit = timeoutRunBlocking {
    val descriptor = RecordingProviderDispatchDescriptor()
    val titleWaitStarted = CompletableDeferred<Unit>()
    val titleWaitRelease = CompletableDeferred<Unit>()
    val titleWaitRequests = mutableListOf<TerminalTitleThreadIdAwaitRequest>()
    val file = createFile(
      provider = descriptor.provider,
      initialMessageMode = AgentInitialMessageMode.PLAN,
      steps = listOf(
        AgentInitialMessageDispatchStep(
          text = "Plan this refactor",
          action = AgentInitialMessageDispatchAction.PROVIDER,
        )
      ),
    )
    val tab = FakeTerminalTab(
      coroutineScope = this,
      terminalTitleThreadIdAwaiter = { provider, expectedThreadId, timeoutMs ->
        titleWaitRequests += TerminalTitleThreadIdAwaitRequest(provider, expectedThreadId, timeoutMs)
        titleWaitStarted.complete(Unit)
        titleWaitRelease.await()
        AgentChatTerminalInputReadiness.READY
      },
    )

    createDispatcher(file, provider = descriptor.provider, descriptor = descriptor).schedule(tab)

    titleWaitStarted.await()
    assertThat(descriptor.requests).isEmpty()
    titleWaitRelease.complete(Unit)
    waitForCondition { file.initialMessageSent }

    val titleWaitRequest = titleWaitRequests.single()
    assertThat(titleWaitRequest.provider).isEqualTo(descriptor.provider)
    assertThat(titleWaitRequest.expectedThreadId).isEqualTo("new-test")
    assertThat(titleWaitRequest.timeoutMs).isGreaterThan(0L)
    val providerRequest = descriptor.requests.single()
    assertThat(providerRequest.threadId).isEqualTo("new-test")
    assertThat(providerRequest.message).isEqualTo("Plan this refactor")
    assertThat(providerRequest.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(file.toSnapshot().runtime.initialPromptRecord?.deliveryChannel)
      .isEqualTo(AgentInitialPromptDeliveryChannel.APP_SERVER)
  }

  @Test
  fun transientBusyRewindRetriesFromFirstInitialMessageStep(): Unit = timeoutRunBlocking {
    val behavior = RewindOnceOnPromptStepBehavior()
    val file = createFile(
      provider = AgentSessionProvider.from("codex"),
      steps = listOf(
        AgentInitialMessageDispatchStep(
          text = "Prepare plan",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(coroutineScope = this)

    createDispatcher(file, behavior = behavior).schedule(tab)

    waitForCondition { file.initialMessageSent }
    assertThat(tab.events).containsExactly("text:Prepare plan", "text:Prepare plan", "text:Refactor this")
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
  fun stoppedPlanModeTextDispatchReportsPromptNotSent(): Unit = timeoutRunBlocking {
    val file = createFile(
      provider = AgentSessionProvider.from("codex"),
      initialMessageMode = AgentInitialMessageMode.PLAN,
      steps = listOf(
        AgentInitialMessageDispatchStep(
          text = "Prepare plan",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
        AgentInitialMessageDispatchStep(
          text = "Refactor this",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
      )
    )
    val tab = FakeTerminalTab(coroutineScope = this)
    val reportedFiles = mutableListOf<AgentChatVirtualFile>()

    createDispatcher(
      file = file,
      behavior = StopBeforeSendBehavior,
      planModeInitialPromptStopReporter = { _, stoppedFile -> reportedFiles += stoppedFile },
    ).schedule(tab)

    waitForCondition { file.initialMessageDispatchSteps.isEmpty() }
    assertThat(tab.events).isEmpty()
    assertThat(file.initialMessageSent).isFalse()
    assertThat(reportedFiles).containsExactly(file)
  }
}

private class DelayedObservedDispatchBehavior(
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
    return true
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

private class TransientBusyObservedDispatchBehavior(
  private val transientBusyRetryCount: Int,
) : AgentChatProviderBehavior {
  val afterSendRetryAttempts: MutableList<Int> = mutableListOf()
  private var afterSendCalls: Int = 0

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return true
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

private class AwaitMoreObservedDispatchBehavior(
  private val awaitMoreCount: Int,
) : AgentChatProviderBehavior {
  val afterSendRetryAttempts: MutableList<Int> = mutableListOf()
  private var afterSendCalls: Int = 0

  override fun requiresPostSendObservation(dispatch: AgentChatInitialMessageDispatchContext): Boolean {
    return true
  }

  override fun afterInitialMessageSendObservation(
    file: AgentChatBehaviorFile,
    dispatch: AgentChatInitialMessageDispatchContext,
    observation: AgentChatInitialMessageSendObservation,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    afterSendRetryAttempts += retryAttempt
    if (afterSendCalls < awaitMoreCount) {
      afterSendCalls++
      return AgentChatInitialMessageRetryDecision.AwaitMorePostSendOutput(backoffMs = 1)
    }
    afterSendCalls++
    return AgentChatInitialMessageRetryDecision.PROCEED
  }
}

private class RewindOnceOnPromptStepBehavior : AgentChatProviderBehavior {
  private var rewound: Boolean = false

  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision {
    if (!rewound && dispatch.stepIndex == 1) {
      rewound = true
      return AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness(backoffMs = 1)
    }
    return AgentChatInitialMessageRetryDecision.PROCEED
  }
}

private fun createDispatcher(
  file: AgentChatVirtualFile,
  provider: AgentSessionProvider = AgentSessionProvider.from("junie"),
  behavior: AgentChatProviderBehavior = resolveAgentChatProviderBehavior(provider),
  descriptor: AgentSessionProviderDescriptor? = null,
  planModeInitialPromptStopReporter: (Project, AgentChatVirtualFile) -> Unit = { _, _ -> },
): AgentChatInitialMessageDispatcher {
  return AgentChatInitialMessageDispatcher(
    project = ProjectManager.getInstance().defaultProject,
    file = file,
    behavior = behavior,
    descriptor = descriptor,
    tabSnapshotWriter = AgentChatTabSnapshotWriter {},
    planModeInitialPromptStopReporter = planModeInitialPromptStopReporter,
  )
}

private object StopBeforeSendBehavior : AgentChatProviderBehavior {
  override suspend fun beforeInitialMessageSend(
    file: AgentChatBehaviorFile,
    tab: AgentChatBehaviorTerminalTab,
    dispatch: AgentChatInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentChatInitialMessageRetryDecision = AgentChatInitialMessageRetryDecision.Stop
}

private fun createFile(
  steps: List<AgentInitialMessageDispatchStep>,
  provider: AgentSessionProvider = AgentSessionProvider.from("junie"),
  initialMessageMode: AgentInitialMessageMode = AgentInitialMessageMode.STANDARD,
): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = "/project",
    threadIdentity = "${provider.value}:new-test",
    shellCommand = emptyList(),
    threadId = "new-test",
    threadTitle = provider.value,
    subAgentId = null,
  ).also { file ->
    file.updateInitialPromptDelivery(
      promptRecord = AgentInitialPromptRecord(
        message = steps.lastOrNull { step ->
          step.recordsPrompt &&
          step.action == AgentInitialMessageDispatchAction.SEND_TEXT &&
          step.text.isNotBlank()
        }?.text,
        mode = initialMessageMode,
        token = "token",
        deliveryStatus = AgentInitialPromptDeliveryStatus.PENDING,
        deliveryChannel = AgentInitialPromptDeliveryChannel.TERMINAL,
      ),
      terminalDispatch = AgentTerminalPromptDispatch(
        steps = steps,
        stepIndex = 0,
      ),
    )
  }
}

private class FakeTerminalTab(
  override val coroutineScope: CoroutineScope,
  private val recentOutputTail: String = "",
  outputObservations: List<AgentChatTerminalOutputObservation> = emptyList(),
  private val terminalTitleThreadIdAwaiter: (suspend (AgentSessionProvider?, String, Long) -> AgentChatTerminalInputReadiness)? = null,
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

  override suspend fun awaitTerminalTitleThreadId(
    provider: AgentSessionProvider?,
    expectedThreadId: String,
    timeoutMs: Long,
  ): AgentChatTerminalInputReadiness {
    return terminalTitleThreadIdAwaiter?.invoke(provider, expectedThreadId, timeoutMs) ?: AgentChatTerminalInputReadiness.READY
  }

  override suspend fun readRecentOutputTail(): String = recentOutputTail

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    events.add("text:$text")
  }

  override suspend fun sendInitialMessageText(
    text: String,
    shouldExecute: Boolean,
    useBracketedPasteMode: Boolean,
  ) {
    sendText(text, shouldExecute, useBracketedPasteMode)
  }
}

private class RecordingProviderDispatchDescriptor : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider = AgentSessionProvider.from("codex")
  override val displayNameKey: String = "test.provider.codex"
  override val newSessionLabelKey: String = "test.new.codex"
  override val icon: Icon = EmptyIcon.ICON_16
  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@RecordingProviderDispatchDescriptor.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }
  override val cliMissingMessageKey: String = "test.cli.missing"
  val requests: MutableList<AgentInitialMessageProviderDispatchRequest> = mutableListOf()

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("codex"))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override suspend fun dispatchInitialMessageToProvider(request: AgentInitialMessageProviderDispatchRequest): Boolean {
    requests += request
    return true
  }
}

private data class TerminalTitleThreadIdAwaitRequest(
  val provider: AgentSessionProvider?,
  val expectedThreadId: String,
  val timeoutMs: Long,
)

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
