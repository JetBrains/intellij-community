// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.lang.reflect.Constructor
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
  fun editorTabActionGroupWrapperIsDumbAware() {
    val firstAction = DumbAwareAction.create("First") { }
    val secondAction = DumbAwareAction.create("Second") { }

    val emptyGroup = buildAgentChatEditorTabActionGroup(emptyList())
    val wrappedSingleActionGroup = buildAgentChatEditorTabActionGroup(listOf(firstAction))
    val existingGroup = object : DefaultActionGroup(firstAction), DumbAware {}
    val reusedGroup = buildAgentChatEditorTabActionGroup(listOf(existingGroup))
    val wrappedMultiActionGroup = buildAgentChatEditorTabActionGroup(listOf(firstAction, secondAction))

    assertThat(emptyGroup).isNull()
    assertThat(wrappedSingleActionGroup).isNotNull
    assertThat(checkNotNull(wrappedSingleActionGroup)).isInstanceOf(DumbAware::class.java)
    assertThat((wrappedSingleActionGroup as DefaultActionGroup).getChildActionsOrStubs())
      .containsExactly(firstAction)
    assertThat(reusedGroup).isSameAs(existingGroup)
    assertThat(wrappedMultiActionGroup).isNotNull
    assertThat(checkNotNull(wrappedMultiActionGroup)).isInstanceOf(DumbAware::class.java)
    assertThat((wrappedMultiActionGroup as DefaultActionGroup).getChildActionsOrStubs())
      .containsExactly(firstAction, secondAction)
  }

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
  fun disposeKeepsInitializedTerminalTabAliveUntilFileClose() {
    val project = testProject()
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentChatLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentChatLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(terminalTabs.closeCalls).isEqualTo(0)

    liveTerminalStore.dispose(project)
  }

  @Test
  fun recreatedEditorReusesInitializedTerminalTab() {
    val project = testProject()
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentChatLiveTerminalStore()
    val liveTerminalRegistry = TestAgentChatLiveTerminalRegistry(project, liveTerminalStore)
    val firstEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )

    firstEditor.selectNotify()
    Disposer.dispose(firstEditor)

    val secondEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )
    secondEditor.selectNotify()

    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(secondEditor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)

    Disposer.dispose(secondEditor)
    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedClosesInitializedTerminalTabWhenNoCopiesRemain() {
    val project = testProject()
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentChatLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentChatLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = false), file)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isFalse()

    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedKeepsInitializedTerminalTabWhenFileIsStillReportedOpen() {
    val project = testProject()
    val terminalTabs = FakeAgentChatTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentChatLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentChatLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = true), file)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    liveTerminalStore.dispose(project)
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

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
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
  fun codexPlanModeWaitsForMcpStartupTailToClearBeforeFirstPlanSend() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after MCP startup"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-mcp-startup",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput(codexMcpStartupTerminalSnapshot())
    Thread.sleep(350)

    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 2 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after MCP startup", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeWaitsForQueueHintTailToClearBeforeFirstPlanSend() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after queue hint"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-queue-hint",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput(codexQueueHintTerminalSnapshot())
    Thread.sleep(350)

    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 2 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after queue hint", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeOldBusyOutputDoesNotBlockOnceLatestTailIsIdle() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after idle tail"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-old-busy-tail",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("'/plan' is disabled while a task is in progress.")
    Thread.sleep(350)

    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 2 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after idle tail", shouldExecute = true),
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

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition { terminalTabs.tab.sentTexts.size == 2 }

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
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

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }
    Thread.sleep(1_300)

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
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
  fun codexPlanModeBusyRetryWaitsForThreadActivityToLeaveBusyStateBeforeResendingPlan() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    terminalTabs.tab.enqueuePostSendOutput("'/plan' is disabled while a task is in progress.")
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after activity clears"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-busy-activity",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for first /plan")
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    file.updateThreadActivity(AgentThreadActivity.PROCESSING)
    Thread.sleep(350)

    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan", shouldExecute = true))

    file.updateThreadActivity(AgentThreadActivity.READY)
    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition(timeoutMs = 5_000) {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 2
    }

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 3 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after activity clears", shouldExecute = true),
      )
  }

  @Test
  fun codexPlanModeBusyResponseWithFormattingNoiseStillRetriesPlanStep() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    terminalTabs.tab.enqueuePostSendOutput("\u001B[31m'/plan'\u001B[0m   is disabled while a\n task is in progress.")
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialMessageDispatchSteps = codexPlanDispatchSteps("Retry after formatted busy output"),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = "token-plan-busy-formatted",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("ready for first /plan")

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    terminalTabs.tab.emitMeaningfulOutput(codexIdleTerminalSnapshot())
    waitForCondition(timeoutMs = 6_000) {
      file.initialMessageDispatchStepIndex == 1 &&
      terminalTabs.tab.sentTexts.size == 2
    }

    terminalTabs.tab.emitMeaningfulOutput("ready for prompt")
    waitForCondition { terminalTabs.tab.sentTexts.size == 3 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("/plan", shouldExecute = true),
        SentTerminalText("Retry after formatted busy output", shouldExecute = true),
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

  @Test
  fun pendingCodexFirstInputPersistsMetadataAndRetriesScopedRefresh() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val snapshotWriter = RecordingSnapshotWriter()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile()
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      snapshotWriter = snapshotWriter,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('a'))

      waitForCondition {
        file.pendingFirstInputAtMs != null &&
        snapshotWriter.snapshots.lastOrNull()?.runtime?.pendingFirstInputAtMs == file.pendingFirstInputAtMs &&
        signalCollector.codexSignals.size >= 2
      }

      assertThat(signalCollector.codexSignals.map { it.single() }).containsOnly(file.projectPath)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun restoredPendingCodexTabResumesScopedRefreshRetriesOnInitialization() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val pendingFirstInputAtMs = System.currentTimeMillis() - 100L
    val file = pendingTestFile(pendingFirstInputAtMs = pendingFirstInputAtMs)
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()

      waitForCondition {
        signalCollector.codexSignals.size >= 2
      }

      assertThat(file.pendingFirstInputAtMs).isEqualTo(pendingFirstInputAtMs)
      assertThat(signalCollector.codexSignals.map { it.single() }).containsOnly(file.projectPath)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun pendingCodexScopedRefreshRetriesStopAfterRebind() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile()
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 100L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('b'))
      waitForCondition { signalCollector.codexSignals.isNotEmpty() }

      file.rebindPendingThread(
        threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.CODEX.value, "thread-42"),
        shellCommand = listOf("codex", "resume", "thread-42"),
        shellEnvVariables = emptyMap(),
        threadId = "thread-42",
        threadTitle = "Recovered thread",
        threadActivity = AgentThreadActivity.READY,
      )

      val signalCountAfterRebind = signalCollector.codexSignals.size
      Thread.sleep(180)

      assertThat(file.isPendingThread).isFalse()
      assertThat(signalCollector.codexSignals).hasSize(signalCountAfterRebind)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun stalePendingCodexTabDoesNotResumeScopedRefreshRetries() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile(
      pendingFirstInputAtMs = System.currentTimeMillis() - AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS - 1L,
    )
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      Thread.sleep(120)

      assertThat(signalCollector.codexSignals).isEmpty()
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun pendingClaudeTabDoesNotStartCodexScopedRefreshRetries() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile(
      provider = AgentSessionProvider.CLAUDE,
      pendingFirstInputAtMs = System.currentTimeMillis() - 100L,
    )
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('c'))
      Thread.sleep(120)

      assertThat(signalCollector.codexSignals).isEmpty()
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
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
  private val mutableKeyEventsFlow: MutableSharedFlow<TerminalKeyEvent> = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = mutableKeyEventsFlow.asSharedFlow()

  @Volatile
  var readinessResult: AgentChatTerminalInputReadiness = AgentChatTerminalInputReadiness.READY

  @Volatile
  private var recentOutputTail: String = ""
  private val postSendOutputQueue: ConcurrentLinkedDeque<PostSendOutput> = ConcurrentLinkedDeque()
  private val emittedOutputChunks: CopyOnWriteArrayList<EmittedOutputChunk> = CopyOnWriteArrayList()
  private val outputVersion: AtomicLong = AtomicLong()

  @JvmField
  val sentTexts: CopyOnWriteArrayList<SentTerminalText> = CopyOnWriteArrayList()

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
    recentOutputTail = normalizedText
    val nextVersion = outputVersion.incrementAndGet()
    emittedOutputChunks += EmittedOutputChunk(version = nextVersion, text = normalizedText)
  }

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  fun emitKeyEvent(awtEvent: KeyEvent) {
    mutableKeyEventsFlow.tryEmit(terminalKeyEvent(awtEvent))
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

  override suspend fun readRecentOutputTail(): String {
    return recentOutputTail.takeLast(4_096)
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

private fun codexMcpStartupTerminalSnapshot(): String {
  return listOf(
    "• Booting MCP server: alpha (0s • esc to interrupt)",
    "",
    "",
    "› Ask Codex to do anything",
    "",
    "  gpt-5.3-codex default · 100% left · /tmp/project",
  ).joinToString(separator = "\n")
}

private fun codexQueueHintTerminalSnapshot(): String {
  return listOf(
    "• Working (0s • esc to interrupt)",
    "",
    "",
    "› Ask Codex to do anything",
    "",
    "  tab to queue message · Plan mode",
  ).joinToString(separator = "\n")
}

private fun codexIdleTerminalSnapshot(): String {
  return listOf(
    "",
    "",
    "",
    "",
    "",
    "",
    "› Ask Codex to do anything",
    "",
    "  ? for shortcuts · 100% context left",
  ).joinToString(separator = "\n")
}

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

private fun pendingTestFile(
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  pendingFirstInputAtMs: Long? = null,
): AgentChatVirtualFile {
  return testFile(
    threadIdentity = buildAgentThreadIdentity(provider.value, "new-thread"),
    shellCommand = listOf(provider.value),
  ).also { file ->
    file.updatePendingMetadata(
      pendingCreatedAtMs = System.currentTimeMillis() - 1_000L,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = "standard",
    )
  }
}

private fun claudeLifecycleTestFile(): AgentChatVirtualFile {
  return testFile(
    threadIdentity = "CLAUDE:session-1",
    shellCommand = listOf("claude", "--resume", "session-1"),
  )
}

private fun testEditor(
  project: Project = testProject(),
  file: AgentChatVirtualFile = testFile(),
  terminalTabs: AgentChatTerminalTabs = FakeAgentChatTerminalTabs(),
  liveTerminalRegistry: AgentChatLiveTerminalRegistry = TestAgentChatLiveTerminalRegistry(project),
  snapshotWriter: AgentChatTabSnapshotWriter = AgentChatTabSnapshotWriter { },
  pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
): AgentChatFileEditor {
  return AgentChatFileEditor(
    project = project,
    file = file,
    terminalTabs = terminalTabs,
    liveTerminalRegistry = liveTerminalRegistry,
    tabSnapshotWriter = snapshotWriter,
    pendingScopedRefreshRetryIntervalMs = pendingScopedRefreshRetryIntervalMs,
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

private class CodexScopedRefreshSignalCollector {
  val codexSignals: CopyOnWriteArrayList<Set<String>> = CopyOnWriteArrayList()
  private val job = object : CoroutineScope {
    override val coroutineContext = Job() + Dispatchers.Default
  }.launch(start = CoroutineStart.UNDISPATCHED) {
    codexScopedRefreshSignals().collect { signal ->
      codexSignals += signal
    }
  }

  fun dispose() {
    job.cancel()
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

private class TestAgentChatLiveTerminalRegistry(
  private val project: Project,
  private val store: AgentChatLiveTerminalStore = AgentChatLiveTerminalStore(),
) : AgentChatLiveTerminalRegistry {
  override fun acquireOrCreate(file: AgentChatVirtualFile, terminalTabs: AgentChatTerminalTabs): AgentChatTerminalTab {
    return store.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs)
  }
}

private fun testFileEditorManager(isFileOpen: Boolean): FileEditorManager {
  val project = testProject()
  val selectedEditorFlow = MutableStateFlow<FileEditor?>(null)
  return object : FileEditorManager() {
    override fun getComposite(file: VirtualFile): FileEditorComposite? = null

    override fun canOpenFile(file: VirtualFile): Boolean = true

    override fun openFile(file: VirtualFile, focusEditor: Boolean): Array<FileEditor> = emptyArray()

    override fun openFile(file: VirtualFile): List<FileEditor> = emptyList()

    override fun closeFile(file: VirtualFile) = Unit

    override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? = null

    override fun getSelectedTextEditor(): Editor? = null

    override fun isFileOpen(file: VirtualFile): Boolean = isFileOpen

    override fun getOpenFiles(): Array<VirtualFile> = emptyArray()

    override fun getOpenFilesWithRemotes(): List<VirtualFile> = emptyList()

    override fun getCurrentFile(): VirtualFile? = null

    override fun getSelectedFiles(): Array<VirtualFile> = emptyArray()

    override fun getSelectedEditors(): Array<FileEditor> = emptyArray()

    override fun getSelectedEditorFlow(): StateFlow<FileEditor?> = selectedEditorFlow

    override fun getSelectedEditor(file: VirtualFile): FileEditor? = null

    override fun getEditors(file: VirtualFile): Array<FileEditor> = emptyArray()

    override fun getAllEditors(file: VirtualFile): Array<FileEditor> = emptyArray()

    override fun getAllEditorList(file: VirtualFile): List<FileEditor> = emptyList()

    override fun getAllEditors(): Array<FileEditor> = emptyArray()

    override fun addTopComponent(editor: FileEditor, component: JComponent) = Unit

    override fun removeTopComponent(editor: FileEditor, component: JComponent) = Unit

    override fun addBottomComponent(editor: FileEditor, component: JComponent) = Unit

    override fun removeBottomComponent(editor: FileEditor, component: JComponent) = Unit

    override fun openFileEditor(descriptor: FileEditorNavigatable, focusEditor: Boolean): List<FileEditor> = emptyList()

    override fun getProject(): Project = project

    override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) = Unit

    override fun runWhenLoaded(editor: Editor, runnable: Runnable) = runnable.run()

    override fun toString(): String = "FileEditorManager(agent-chat-editor-lifecycle-test)"
  }
}

private fun keyTyped(keyChar: Char): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, keyChar)
}

private fun keyPressed(keyCode: Int): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED)
}

private fun terminalKeyEvent(awtEvent: KeyEvent): TerminalKeyEvent {
  return TERMINAL_KEY_EVENT_CONSTRUCTOR.newInstance(awtEvent, TerminalOffset.ZERO) as TerminalKeyEvent
}

private val TERMINAL_KEY_EVENT_CONSTRUCTOR: Constructor<*> by lazy {
  Class.forName("com.intellij.terminal.frontend.view.TerminalKeyEventImpl")
    .getDeclaredConstructor(KeyEvent::class.java, TerminalOffset::class.java)
    .apply { isAccessible = true }
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
