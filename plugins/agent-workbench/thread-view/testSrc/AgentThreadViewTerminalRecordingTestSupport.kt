// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

class RecordingAgentThreadViewTerminalHarness {
  private val terminalTabs = RecordingAgentThreadViewTerminalTabs()
  private val openedFileSnapshotsByKey: ConcurrentHashMap<String, RecordingAgentThreadViewOpenedFileSnapshot> = ConcurrentHashMap()
  private val openedFileSnapshotsFlow: MutableStateFlow<List<RecordingAgentThreadViewOpenedFileSnapshot>> = MutableStateFlow(emptyList())

  val startupLaunchSpecs: List<AgentSessionTerminalLaunchSpec>
    get() = terminalTabs.startupLaunchSpecs.toList()

  val sentTexts: List<RecordingTerminalSentText>
    get() = terminalTabs.tab.sentTexts.toList()

  fun registerEditorFactory(parentDisposable: Disposable) {
    registerAgentThreadViewFileEditorFactoryOverrideForTests(
      RecordingAgentThreadViewFileEditorFactory(
        terminalTabs = terminalTabs,
        recordSnapshot = ::recordSnapshot,
      ),
      parentDisposable,
    )
  }

  fun setRunning() {
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
  }

  suspend fun activateAgentThreadViewEditor(project: Project, file: VirtualFile): Int {
    return withContext(Dispatchers.UiWithModelAccess) {
      val threadViewFile = file as? AgentThreadViewVirtualFile ?: return@withContext 0
      val manager = FileEditorManagerEx.getInstanceEx(project)
      activateAgentThreadViewEditors(manager = manager, file = threadViewFile)
    }
  }

  suspend fun awaitCreateCalls(expected: Int, timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      terminalTabs.createCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitSentTextsStayAt(
    expectedSize: Int,
    timeoutMs: Long = TERMINAL_HARNESS_NEGATIVE_WAIT_TIMEOUT_MS,
  ): List<RecordingTerminalSentText> {
    val unexpectedTexts = withTimeoutOrNull(timeoutMs.milliseconds) {
      terminalTabs.tab.sentTextsFlow.first { texts -> texts.size > expectedSize }
    }
    return unexpectedTexts ?: terminalTabs.tab.sentTexts.toList()
  }

  suspend fun awaitInitialMessageSent(timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS): RecordingAgentThreadViewOpenedFileSnapshot {
    return withTimeout(timeoutMs.milliseconds) {
      openedFileSnapshotsFlow.first { snapshots ->
        snapshots.any { snapshot -> snapshot.initialMessageSent }
      }.single { snapshot -> snapshot.initialMessageSent }
    }
  }

  private fun recordSnapshot(snapshot: AgentThreadViewTabSnapshot) {
    openedFileSnapshotsByKey[snapshot.tabKey.toString()] = recordingSnapshot(snapshot)
    openedFileSnapshotsFlow.value = openedFileSnapshotsByKey.values.toList()
  }

  private fun activateAgentThreadViewEditors(
    manager: FileEditorManagerEx,
    file: AgentThreadViewVirtualFile,
  ): Int {
    return agentThreadViewEditors(manager = manager, file = file)
      .onEach { editor ->
        editor.showComponentForTests()
        editor.selectNotify()
      }
      .size
  }

  private fun agentThreadViewEditors(
    manager: FileEditorManagerEx,
    file: AgentThreadViewVirtualFile,
  ): List<AgentThreadViewFileEditor> {
    return manager.getAllEditors(file).filterIsInstance<AgentThreadViewFileEditor>()
  }
}

suspend fun disposeAgentThreadViewLiveTerminalsForTest(project: Project) {
  withContext(Dispatchers.UiWithModelAccess) {
    project.service<AgentThreadViewLiveTerminalRegistryService>().disposeLiveTerminalsForTest()
  }
}

data class RecordingTerminalSentText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

data class RecordingAgentThreadViewOpenedFileSnapshot(
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  @JvmField val initialMessageDispatchStepIndex: Int,
  @JvmField val initialMessageDispatchStepCount: Int,
  @JvmField val initialMessageSent: Boolean,
)

private class RecordingAgentThreadViewFileEditorFactory(
  private val terminalTabs: RecordingAgentThreadViewTerminalTabs,
  private val recordSnapshot: (AgentThreadViewTabSnapshot) -> Unit,
) : AgentThreadViewFileEditorFactory {
  override fun create(project: Project, file: AgentThreadViewVirtualFile, editorCoroutineScope: CoroutineScope?): AgentThreadViewFileEditor {
    return AgentThreadViewFileEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
        recordSnapshot(snapshot)
        project.service<AgentThreadViewTabsService>().upsert(snapshot)
      },
      editorCoroutineScope = editorCoroutineScope,
    ).also { editor ->
      editor.showComponentForTests()
    }
  }
}

private class RecordingAgentThreadViewTerminalTabs : AgentThreadViewTerminalTabs {
  val createCalls: AtomicInteger = AtomicInteger()
  val createCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)
  val startupLaunchSpecs: CopyOnWriteArrayList<AgentSessionTerminalLaunchSpec> = CopyOnWriteArrayList()
  val tab: RecordingAgentThreadViewTerminalTab = RecordingAgentThreadViewTerminalTab()

  override fun createTab(
    project: Project,
    file: AgentThreadViewVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    startupLaunchSpecs += startupLaunchSpec
    createCallsFlow.value = createCalls.incrementAndGet()
    return tab
  }

  override fun closeTab(project: Project, tab: AgentThreadViewTerminalTab) {
    tab.coroutineScope.cancel()
  }
}

private class RecordingAgentThreadViewTerminalTab : AgentThreadViewTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")

  @Suppress("RAW_SCOPE_CREATION")
  override val coroutineScope: CoroutineScope = CoroutineScope(Job())
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()

  val sentTexts: CopyOnWriteArrayList<RecordingTerminalSentText> = CopyOnWriteArrayList()
  val sentTextsFlow: MutableStateFlow<List<RecordingTerminalSentText>> = MutableStateFlow(emptyList())

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentThreadViewTerminalOutputCheckpoint {
    return AgentThreadViewTerminalOutputCheckpoint(regularEndOffset = 0, alternativeEndOffset = 0)
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentThreadViewTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentThreadViewTerminalOutputObservation {
    val deadline = System.currentTimeMillis() + timeoutMs
    val pollIntervalMs = idleMs.coerceIn(10, 50)
    while (true) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        return AgentThreadViewTerminalOutputObservation(
          readiness = AgentThreadViewTerminalInputReadiness.TERMINATED,
          text = "",
        )
      }
      if (System.currentTimeMillis() >= deadline) {
        return AgentThreadViewTerminalOutputObservation(
          readiness = AgentThreadViewTerminalInputReadiness.TIMEOUT,
          text = "",
        )
      }
      delay(pollIntervalMs.milliseconds)
    }
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentThreadViewTerminalOutputCheckpoint?,
  ): AgentThreadViewTerminalInputReadiness {
    return if (sessionState.value == TerminalViewSessionState.Terminated) {
      AgentThreadViewTerminalInputReadiness.TERMINATED
    }
    else {
      AgentThreadViewTerminalInputReadiness.READY
    }
  }

  override suspend fun readRecentOutputTail(): String = ""

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    recordSentText(
      RecordingTerminalSentText(
        text = text,
        shouldExecute = shouldExecute,
        useBracketedPasteMode = useBracketedPasteMode,
      )
    )
  }

  override suspend fun sendInitialMessageText(
    text: String,
    shouldExecute: Boolean,
    useBracketedPasteMode: Boolean,
  ) {
    recordSentText(
      RecordingTerminalSentText(
        text = text,
        shouldExecute = shouldExecute,
        useBracketedPasteMode = useBracketedPasteMode,
      )
    )
  }

  private fun recordSentText(sentText: RecordingTerminalSentText) {
    sentTexts += sentText
    sentTextsFlow.value = sentTexts.toList()
  }
}

private fun recordingSnapshot(snapshot: AgentThreadViewTabSnapshot): RecordingAgentThreadViewOpenedFileSnapshot {
  return RecordingAgentThreadViewOpenedFileSnapshot(
    projectPath = snapshot.identity.projectPath,
    threadIdentity = snapshot.identity.threadIdentity,
    threadId = snapshot.runtime.threadId,
    threadTitle = snapshot.runtime.threadTitle,
    initialMessageDispatchStepIndex = snapshot.runtime.initialMessageDispatchStepIndex,
    initialMessageDispatchStepCount = snapshot.runtime.initialMessageDispatchSteps.size,
    initialMessageSent = snapshot.runtime.initialMessageSent,
  )
}

private const val TERMINAL_HARNESS_WAIT_TIMEOUT_MS: Long = 30_000
private const val TERMINAL_HARNESS_NEGATIVE_WAIT_TIMEOUT_MS: Long = 500
