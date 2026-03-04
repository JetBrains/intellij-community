// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class AgentChatFileEditorLifecycleTest {
  @Test
  fun preferredFocusedComponentDoesNotStartTerminalInitialization() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    val preferred = editor.preferredFocusedComponent

    assertThat(preferred).isSameAs(editor.component)
    assertThat(terminalTabs.createCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifyInitializesTerminalOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun disposeClosesInitializedTerminalTabOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
  }

  @Test
  fun disposeWithoutInitializationDoesNotCloseTerminalTab() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Send even if output is silent", shouldExecute = true))
  }

  @Test
  fun codexPlanModeTimeoutReadinessWaitsWithoutSending() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.readinessResult = AgentChatTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/plan Send only after explicit readiness",
        initialMessageToken = "token-plan-timeout",
        initialMessageSent = false,
        initialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      )
    }
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    Disposer.dispose(editor)
  }

  @Test
  fun codexPlanModeTimeoutThenReadySendsInitialMessageOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    terminalTabs.tab.enqueueReadiness(
      AgentChatTerminalInputReadiness.TIMEOUT,
      AgentChatTerminalInputReadiness.READY,
    )
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/plan Send after retry",
        initialMessageToken = "token-plan-timeout-ready",
        initialMessageSent = false,
        initialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      )
    }
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan Send after retry", shouldExecute = true))
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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = file,
      terminalTabs = terminalTabs,
    )

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
  override val keyEventsFlow: Flow<*> = emptyFlow<Unit>()
  var readinessResult: AgentChatTerminalInputReadiness = AgentChatTerminalInputReadiness.READY
  private val readinessQueue: ArrayDeque<AgentChatTerminalInputReadiness> = ArrayDeque()

  @JvmField val sentTexts: MutableList<SentTerminalText> = mutableListOf()

  fun enqueueReadiness(vararg readiness: AgentChatTerminalInputReadiness) {
    readinessQueue.addAll(readiness.asList())
  }

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override fun sendText(text: String, shouldExecute: Boolean) {
    sentTexts += SentTerminalText(text, shouldExecute)
  }

  override suspend fun awaitInitialMessageReadiness(timeoutMs: Long, idleMs: Long): AgentChatTerminalInputReadiness {
    return if (readinessQueue.isEmpty()) readinessResult else readinessQueue.removeFirst()
  }
}

private data class SentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
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
