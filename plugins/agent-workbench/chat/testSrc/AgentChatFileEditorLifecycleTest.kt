// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class AgentChatFileEditorLifecycleTest {
  @Test
  fun preferredFocusedComponentDoesNotStartTerminalInitialization() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    val preferred = editor.preferredFocusedComponent

    assertThat(preferred).isSameAs(editor.component)
    assertThat(terminalTabs.createCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifyInitializesTerminalOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    editor.selectNotify()
    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun disposeClosesInitializedTerminalTabOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    editor.selectNotify()
    Disposer.dispose(editor)
    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
  }

  @Test
  fun disposeWithoutInitializationDoesNotCloseTerminalTab() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifySendsInitialMessageOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Refactor selected code",
        initialMessageToken = "token-1",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    editor.selectNotify()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Refactor selected code", shouldExecute = true))
  }

  @Test
  fun flushPendingInitialMessageWaitsForRunningSessionState() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "Apply follow-up changes",
      initialMessageToken = "token-follow-up",
      initialMessageSent = false,
    )

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Apply follow-up changes", shouldExecute = true))
  }

  @Test
  fun disposeBeforeSessionRunningSkipsInitialMessageSend() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Generate tests",
        initialMessageToken = "token-dispose",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    Disposer.dispose(editor)
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(file.initialMessageSent).isFalse()
  }

  @Test
  fun waitingForSessionRunningSendsLatestInitialMessageMetadata() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "First draft",
      initialMessageToken = "token-1",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    file.updateInitialMessageMetadata(
      initialComposedMessage = "Second draft",
      initialMessageToken = "token-2",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Second draft", shouldExecute = true))
  }

  @Test
  fun terminatedSessionDoesNotSendInitialMessage() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Do not send",
        initialMessageToken = "token-term",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Terminated)
    Thread.sleep(100)

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  @Test
  fun timeoutReadinessStillSendsInitialMessage() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Send even if output is silent",
        initialMessageToken = "token-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Send even if output is silent", shouldExecute = true))
  }

  @Test
  fun claudeMenuCommandInitialMessageUsesTypedInputInsteadOfBracketedPaste() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = testFile(
      threadIdentity = "CLAUDE:session-1",
      shellCommand = listOf("claude", "--resume", "session-1"),
    ).also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/mcp",
        initialMessageToken = "token-menu",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/mcp", shouldExecute = true, useBracketedPasteMode = false))
  }

  @Test
  fun codexPlanModeTimeoutReadinessWaitsWithoutSending() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Send only after explicit readiness"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    Disposer.dispose(editor)
  }

  @Test
  fun codexPlanModeTimeoutThenFreshReadySignalsSendPlanStepThenPrompt() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Send after retry"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-timeout-ready",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.emitMeaningfulOutput("ready for /plan")
    waitForCondition {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 1
    }

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 2 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Send after retry", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeBusyResponseRetriesPlanStepBeforePrompt() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    terminalTabs.tab.enqueuePostSendOutput("'/plan' is disabled while a task is in progress.")
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after busy output"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-busy",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for first /plan")
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    waitForCondition(timeoutMs = 5_000) {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 2
    }

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
      )

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 3 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after busy output", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeRepeatedBusyResponsesKeepRetryingPlanStepBeforePrompt() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    terminalTabs.tab.enqueuePostSendOutput(
      "'/plan' is disabled while a task is in progress.",
      "'/plan' is disabled while a task is in progress.",
    )
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after repeated busy output"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-busy-repeated",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for first /plan")

    waitForCondition(timeoutMs = 6_000) {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 3
    }

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
      )

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 4 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after repeated busy output", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeDelayedBusyResponseWithinObservationWindowRetriesPlanStep() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    terminalTabs.tab.enqueueDelayedPostSendOutput(
      delayMs = 900,
      output = "'/plan' is disabled while a task is in progress.",
    )
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after delayed busy output"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-busy-delayed",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for first /plan")

    waitForCondition(timeoutMs = 6_000) {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 2
    }

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
      )

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 3 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after delayed busy output", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeSuccessfulPlanStepPersistsPartialProgress() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val snapshotWriter = RecordingSnapshotWriter()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps(
          prompt = "Wait for explicit readiness",
          promptTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        ),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-partial",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs, snapshotWriter = snapshotWriter)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for /plan")
    waitForCondition { snapshotWriter.snapshots.isNotEmpty() }

    assertThat(file.initialMessageDispatchStepIndex).isEqualTo(1)
    assertThat(file.initialMessageSent).isFalse()
    assertThat(file.initialComposedMessage).isEqualTo("Wait for explicit readiness")
    assertThat(snapshotWriter.snapshots.last().runtime.initialMessageDispatchStepIndex).isEqualTo(1)
    assertThat(snapshotWriter.snapshots.last().runtime.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))
    Disposer.dispose(editor)
  }

  @Test
  fun codexPlannerPrefixStillFallsBackOnTimeout() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/planner still fallback",
        initialMessageToken = "token-planner-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/planner still fallback", shouldExecute = true))
  }

  @Test
  fun nonCodexPlanCommandStillFallsBackOnTimeout() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile(
      threadIdentity = "CLAUDE:thread-1",
      shellCommand = listOf("claude", "--resume", "thread-1"),
    ).also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/plan fallback for non-codex",
        initialMessageToken = "token-non-codex-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan fallback for non-codex", shouldExecute = true))
  }

  @Test
  fun timeoutPolicyUsesLatestInitialMessageMetadata() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "Fallback candidate",
      initialMessageToken = "token-timeout-latest-1",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    file.updateInitialMessageMetadata(
      initialComposedMessage = "/plan Wait for readiness",
      initialMessageToken = "token-timeout-latest-2",
      initialMessageSent = false,
      initialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
    )
    editor.flushPendingInitialMessageIfInitialized()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    Disposer.dispose(editor)
  }

  @Test
  fun slashNewTrackerIgnoresPartialCommandsAndHandlesBackspaceCorrection() {
    val tracker = AgentChatTerminalCommandTracker()

    "/new branch".forEach { tracker.record(keyTyped(it)) }
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("/new branch")

    "/newx".forEach { tracker.record(keyTyped(it)) }
    tracker.record(keyPressed(KeyEvent.VK_BACK_SPACE))
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("/new")

    "echo /new".forEach { tracker.record(keyTyped(it)) }
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("echo /new")
  }
}

private class FakeAgentChatTerminalTabs : AgentChatTerminalTabs {
  var createCalls: Int = 0
  var closeCalls: Int = 0
  val tab = FakeAgentChatTerminalTab()

  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    createCalls++
    return tab
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    closeCalls++
    (tab as? FakeAgentChatTerminalTab)?.coroutineScope?.cancel()
  }
}

private class FakeAgentChatTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()
  @Volatile var readinessResult: AgentChatTerminalInputReadiness = AgentChatTerminalInputReadiness.READY
  private val postSendOutputQueue: ConcurrentLinkedDeque<PostSendOutput> = ConcurrentLinkedDeque()
  private val emittedOutputChunks: CopyOnWriteArrayList<EmittedOutputChunk> = CopyOnWriteArrayList()
  private val outputVersion: AtomicLong = AtomicLong()

  @JvmField val sentTexts: CopyOnWriteArrayList<SentTerminalText> = CopyOnWriteArrayList()

  fun enqueuePostSendOutput(vararg outputs: String) {
    postSendOutputQueue.addAll(outputs.map { output -> PostSendOutput(text = output, delayMs = 0) })
  }

  fun enqueueDelayedPostSendOutput(delayMs: Long, output: String) {
    postSendOutputQueue.add(PostSendOutput(text = output, delayMs = delayMs))
  }

  fun emitMeaningfulOutput(text: String = "ready") {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    val nextVersion = outputVersion.incrementAndGet()
    emittedOutputChunks += EmittedOutputChunk(version = nextVersion, text = normalizedText)
  }

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    val currentOutputVersion = outputVersion.get()
    return AgentChatTerminalOutputCheckpoint(
      regularEndOffset = currentOutputVersion,
      alternativeEndOffset = currentOutputVersion,
    )
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation {
    val deadline = System.currentTimeMillis() + timeoutMs
    val pollIntervalMs = idleMs.coerceIn(10, 50)
    while (true) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        return AgentChatTerminalOutputObservation(
          readiness = AgentChatTerminalInputReadiness.TERMINATED,
          text = readOutputSince(checkpoint),
        )
      }
      val text = readOutputSince(checkpoint)
      if (text.isNotEmpty()) {
        return AgentChatTerminalOutputObservation(
          readiness = AgentChatTerminalInputReadiness.READY,
          text = text,
        )
      }
      if (System.currentTimeMillis() >= deadline) {
        return AgentChatTerminalOutputObservation(
          readiness = AgentChatTerminalInputReadiness.TIMEOUT,
          text = text,
        )
      }
      Thread.sleep(pollIntervalMs)
    }
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += SentTerminalText(text, shouldExecute, useBracketedPasteMode)
    postSendOutputQueue.pollFirst()?.let { output ->
      if (output.delayMs <= 0) {
        emitMeaningfulOutput(output.text)
      }
      else {
        Thread {
          Thread.sleep(output.delayMs)
          emitMeaningfulOutput(output.text)
        }
          .apply { isDaemon = true }
          .start()
      }
    }
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness {
    if (sessionState.value == TerminalViewSessionState.Terminated) {
      return AgentChatTerminalInputReadiness.TERMINATED
    }
    if (hasMeaningfulOutputSince(checkpoint)) {
      return AgentChatTerminalInputReadiness.READY
    }
    return readinessResult
  }

  private fun hasMeaningfulOutputSince(checkpoint: AgentChatTerminalOutputCheckpoint?): Boolean {
    val baseline = checkpoint?.regularEndOffset ?: Long.MIN_VALUE
    return emittedOutputChunks.any { chunk -> chunk.version > baseline }
  }

  private fun readOutputSince(checkpoint: AgentChatTerminalOutputCheckpoint): String {
    return emittedOutputChunks
      .filter { chunk -> chunk.version > checkpoint.regularEndOffset }
      .joinToString(separator = "\n") { chunk -> chunk.text }
  }
}

private data class EmittedOutputChunk(
  @JvmField val version: Long,
  @JvmField val text: String,
)

private data class PostSendOutput(
  @JvmField val text: String,
  @JvmField val delayMs: Long,
)

private data class SentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

private fun testFile(
  threadIdentity: String = "CODEX:thread-1",
  shellCommand: List<String> = listOf("codex", "resume", "thread-1"),
): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = "/work/project-a",
    threadIdentity = threadIdentity,
    shellCommand = shellCommand,
    threadId = "thread-1",
    threadTitle = "Thread",
    subAgentId = null,
    projectHash = "hash-1",
  )
}

private fun testEditor(
  file: AgentChatVirtualFile = testFile(),
  terminalTabs: AgentChatTerminalTabs = FakeAgentChatTerminalTabs(),
  snapshotWriter: AgentChatTabSnapshotWriter = AgentChatTabSnapshotWriter { },
): AgentChatFileEditor {
  return AgentChatFileEditor(
    project = testProject(),
    file = file,
    terminalTabs = terminalTabs,
    tabSnapshotWriter = snapshotWriter,
  )
}

private fun codexPlanDispatchSteps(
  prompt: String,
  promptTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
): List<AgentInitialMessageDispatchStep> {
  return listOf(
    AgentInitialMessageDispatchStep(
      text = "/plan",
      timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
    ),
    AgentInitialMessageDispatchStep(
      text = prompt,
      timeoutPolicy = promptTimeoutPolicy,
    ),
  )
}

private class RecordingSnapshotWriter : AgentChatTabSnapshotWriter {
  val snapshots: MutableList<AgentChatTabSnapshot> = mutableListOf()

  override suspend fun upsert(snapshot: AgentChatTabSnapshot) {
    snapshots += snapshot
  }
}

private fun testProject(): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "isDisposed" -> false
      "toString" -> "Project(agent-chat-editor-lifecycle-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}

private fun keyTyped(keyChar: Char): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, keyChar)
}

private fun keyPressed(keyCode: Int): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED)
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}

private fun waitForCondition(timeoutMs: Long = 2_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    Thread.sleep(10)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
