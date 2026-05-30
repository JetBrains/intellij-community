// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.event.KeyEvent
import java.lang.reflect.Constructor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatConcreteThreadRebindControllerTest {
  @Test
  fun slashNewPersistsAnchorAndRetriesScopedRefresh(): Unit = runBlocking {
    val file = concreteCodexFile()
    val tab = ConcreteRebindTestTerminalTab()
    val snapshots = ConcreteRebindRecordingSnapshotWriter()
    val signals = CopyOnWriteArrayList<ConcreteRebindRefreshSignal>()
    val controller = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = TestConcreteThreadRebindBehavior,
      tabSnapshotWriter = snapshots,
      currentTimeProvider = { 1_000L },
      retryIntervalMs = 25L,
      notifyRefresh = { provider, projectPath -> signals += ConcreteRebindRefreshSignal(provider, projectPath) },
    )

    try {
      tab.setSessionState(TerminalViewSessionState.Running)
      controller.attach(tab = tab, descriptor = null)
      tab.emitCommand("/new")

      waitForCondition {
        file.newThreadRebindRequestedAtMs == 1_000L &&
        snapshots.snapshots.lastOrNull()?.runtime?.newThreadRebindRequestedAtMs == 1_000L &&
        signals.size >= 2
      }

      assertThat(signals).allSatisfy { signal ->
        assertThat(signal.provider).isEqualTo(AgentSessionProvider.CODEX)
        assertThat(signal.projectPath).isEqualTo(PROJECT_PATH)
      }
    }
    finally {
      controller.dispose()
      tab.dispose()
    }
  }

  @Test
  fun restoredConcreteTabAwaitingNewThreadRebindResumesScopedRefreshRetries(): Unit = runBlocking {
    val file = concreteCodexFile().also { it.updateNewThreadRebindRequestedAtMs(1_000L) }
    val tab = ConcreteRebindTestTerminalTab()
    val signals = CopyOnWriteArrayList<ConcreteRebindRefreshSignal>()
    val controller = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = TestConcreteThreadRebindBehavior,
      tabSnapshotWriter = ConcreteRebindRecordingSnapshotWriter(),
      currentTimeProvider = { 1_100L },
      retryIntervalMs = 25L,
      notifyRefresh = { provider, projectPath -> signals += ConcreteRebindRefreshSignal(provider, projectPath) },
    )

    try {
      tab.setSessionState(TerminalViewSessionState.Running)
      controller.attach(tab = tab, descriptor = null)

      waitForCondition { signals.size >= 2 }

      assertThat(file.newThreadRebindRequestedAtMs).isEqualTo(1_000L)
      assertThat(signals).allSatisfy { signal ->
        assertThat(signal.provider).isEqualTo(AgentSessionProvider.CODEX)
        assertThat(signal.projectPath).isEqualTo(PROJECT_PATH)
      }
    }
    finally {
      controller.dispose()
      tab.dispose()
    }
  }

  @Test
  fun restoredConcreteTabWithExpiredNewThreadRebindAnchorClearsAnchorWithoutScopedRefresh(): Unit = runBlocking {
    val file = concreteCodexFile().also { it.updateNewThreadRebindRequestedAtMs(1_000L) }
    val tab = ConcreteRebindTestTerminalTab()
    val snapshots = ConcreteRebindRecordingSnapshotWriter()
    val signals = CopyOnWriteArrayList<ConcreteRebindRefreshSignal>()
    val controller = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = TestConcreteThreadRebindBehavior,
      tabSnapshotWriter = snapshots,
      currentTimeProvider = { 1_000L + AgentSessionThreadRebindPolicy.CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS },
      retryIntervalMs = 25L,
      notifyRefresh = { provider, projectPath -> signals += ConcreteRebindRefreshSignal(provider, projectPath) },
    )

    try {
      tab.setSessionState(TerminalViewSessionState.Running)
      controller.attach(tab = tab, descriptor = null)

      waitForCondition {
        file.newThreadRebindRequestedAtMs == null &&
        snapshots.snapshots.isNotEmpty() &&
        snapshots.snapshots.lastOrNull()?.runtime?.newThreadRebindRequestedAtMs == null
      }

      assertThat(signals).isEmpty()
    }
    finally {
      controller.dispose()
      tab.dispose()
    }
  }

  @Test
  fun concreteScopedRefreshRetriesStopAfterRebind(): Unit = runBlocking {
    val file = concreteCodexFile()
    val tab = ConcreteRebindTestTerminalTab()
    val signals = CopyOnWriteArrayList<ConcreteRebindRefreshSignal>()
    val controller = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = TestConcreteThreadRebindBehavior,
      tabSnapshotWriter = ConcreteRebindRecordingSnapshotWriter(),
      currentTimeProvider = { 1_000L },
      retryIntervalMs = 100L,
      notifyRefresh = { provider, projectPath -> signals += ConcreteRebindRefreshSignal(provider, projectPath) },
    )

    try {
      tab.setSessionState(TerminalViewSessionState.Running)
      controller.attach(tab = tab, descriptor = null)
      tab.emitCommand("/new")
      waitForCondition { signals.isNotEmpty() && file.newThreadRebindRequestedAtMs == 1_000L }

      assertThat(file.rebindConcreteThread(
        threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.CODEX.value, "thread-2"),
        threadId = "thread-2",
        threadTitle = "New thread",
        threadActivity = AgentThreadActivity.READY,
      )).isTrue()

      val signalCountAfterRebind = signals.size
      kotlinx.coroutines.delay(160.milliseconds)

      assertThat(file.newThreadRebindRequestedAtMs).isNull()
      assertThat(signals).hasSize(signalCountAfterRebind)
    }
    finally {
      controller.dispose()
      tab.dispose()
    }
  }

  @Test
  fun restoredConcreteTabDoesNotResumeScopedRefreshRetriesForTerminatedSession(): Unit = runBlocking {
    val file = concreteCodexFile().also { it.updateNewThreadRebindRequestedAtMs(1_000L) }
    val tab = ConcreteRebindTestTerminalTab()
    val signals = CopyOnWriteArrayList<ConcreteRebindRefreshSignal>()
    val controller = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = TestConcreteThreadRebindBehavior,
      tabSnapshotWriter = ConcreteRebindRecordingSnapshotWriter(),
      currentTimeProvider = { 1_100L },
      retryIntervalMs = 25L,
      notifyRefresh = { provider, projectPath -> signals += ConcreteRebindRefreshSignal(provider, projectPath) },
    )

    try {
      tab.setSessionState(TerminalViewSessionState.Terminated)
      controller.attach(tab = tab, descriptor = null)
      kotlinx.coroutines.delay(80.milliseconds)

      assertThat(file.newThreadRebindRequestedAtMs).isEqualTo(1_000L)
      assertThat(signals).isEmpty()
    }
    finally {
      controller.dispose()
      tab.dispose()
    }
  }
}

private object TestConcreteThreadRebindBehavior : AgentChatProviderBehavior {
  override fun supportsConcreteNewThreadRebind(
    file: AgentChatVirtualFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean {
    return file.provider == AgentSessionProvider.CODEX && !file.isPendingThread && file.subAgentId == null
  }

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean = command == "/new"
}

private class ConcreteRebindTestTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
  private val mutableKeyEventsFlow = MutableSharedFlow<TerminalKeyEvent>(replay = 16, extraBufferCapacity = 16)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = mutableKeyEventsFlow.asSharedFlow()

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  fun emitCommand(command: String) {
    command.forEach { char -> emitKeyEvent(keyTyped(char)) }
    emitKeyEvent(enterKeyPressed())
  }

  fun dispose() {
    coroutineScope.cancel()
  }

  private fun emitKeyEvent(event: KeyEvent) {
    mutableKeyEventsFlow.tryEmit(terminalKeyEvent(event))
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint = AgentChatTerminalOutputCheckpoint(0L, 0L)

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation = AgentChatTerminalOutputObservation(AgentChatTerminalInputReadiness.READY, "")

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness = AgentChatTerminalInputReadiness.READY

  override suspend fun readRecentOutputTail(): String = ""

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) = Unit
}

private class ConcreteRebindRecordingSnapshotWriter : AgentChatTabSnapshotWriter {
  val snapshots: CopyOnWriteArrayList<AgentChatTabSnapshot> = CopyOnWriteArrayList()

  override suspend fun upsert(snapshot: AgentChatTabSnapshot) {
    snapshots += snapshot
  }
}

private data class ConcreteRebindRefreshSignal(
  val provider: AgentSessionProvider,
  @JvmField val projectPath: String,
)

private fun concreteCodexFile(): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = PROJECT_PATH,
    threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.CODEX.value, "thread-1"),
    shellCommand = listOf("codex", "resume", "thread-1"),
    threadId = "thread-1",
    threadTitle = "Original thread",
    subAgentId = null,
    projectHash = "hash-1",
  )
}

private fun keyTyped(keyChar: Char): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, keyChar)
}

private fun enterKeyPressed(): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED)
}

private fun terminalKeyEvent(awtEvent: KeyEvent): TerminalKeyEvent {
  return TERMINAL_KEY_EVENT_CONSTRUCTOR.newInstance(awtEvent, TerminalOffset.ZERO) as TerminalKeyEvent
}

private val TERMINAL_KEY_EVENT_CONSTRUCTOR: Constructor<*> by lazy {
  Class.forName("com.intellij.terminal.frontend.view.TerminalKeyEventImpl")
    .getDeclaredConstructor(KeyEvent::class.java, TerminalOffset::class.java)
    .apply { isAccessible = true }
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

private const val PROJECT_PATH: String = "/work/project-a"
