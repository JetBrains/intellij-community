// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

class RecordingAgentChatTerminalHarness {
  private val terminalTabs = RecordingAgentChatTerminalTabs()
  private val openedFileSnapshotsByKey: ConcurrentHashMap<String, RecordingAgentChatOpenedFileSnapshot> = ConcurrentHashMap()
  private val openedFileSnapshotsFlow: MutableStateFlow<List<RecordingAgentChatOpenedFileSnapshot>> = MutableStateFlow(emptyList())

  val startupLaunchSpecs: List<AgentSessionTerminalLaunchSpec>
    get() = terminalTabs.startupLaunchSpecs.toList()

  val sentTexts: List<RecordingTerminalSentText>
    get() = terminalTabs.tab.sentTexts.toList()

  val backTabCalls: Int
    get() = terminalTabs.tab.backTabCalls.get()

  fun registerEditorFactory(parentDisposable: Disposable) {
    registerAgentChatFileEditorFactoryOverrideForTests(
      RecordingAgentChatFileEditorFactory(
        terminalTabs = terminalTabs,
        recordSnapshot = ::recordSnapshot,
      ),
      parentDisposable,
    )
  }

  fun setRunning() {
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
  }

  suspend fun activateAgentChatEditor(project: Project, file: VirtualFile): Int {
    return withContext(Dispatchers.UiWithModelAccess) {
      val chatFile = file as? AgentChatVirtualFile ?: return@withContext 0
      val manager = FileEditorManagerEx.getInstanceEx(project)
      activateAgentChatEditors(manager = manager, file = chatFile)
    }
  }

  suspend fun awaitCreateCalls(expected: Int, timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      terminalTabs.createCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitBackTabCalls(expected: Int, timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      terminalTabs.tab.backTabCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitSentTexts(expectedSize: Int, timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS): List<RecordingTerminalSentText> {
    return withTimeout(timeoutMs.milliseconds) {
      terminalTabs.tab.sentTextsFlow.first { texts -> texts.size >= expectedSize }
    }
  }

  suspend fun awaitInitialMessageSent(timeoutMs: Long = TERMINAL_HARNESS_WAIT_TIMEOUT_MS): RecordingAgentChatOpenedFileSnapshot {
    return withTimeout(timeoutMs.milliseconds) {
      openedFileSnapshotsFlow.first { snapshots ->
        snapshots.any { snapshot -> snapshot.initialMessageSent }
      }.single { snapshot -> snapshot.initialMessageSent }
    }
  }

  private fun recordSnapshot(snapshot: AgentChatTabSnapshot) {
    openedFileSnapshotsByKey[snapshot.tabKey.toString()] = recordingSnapshot(snapshot)
    openedFileSnapshotsFlow.value = openedFileSnapshotsByKey.values.toList()
  }

  private fun activateAgentChatEditors(
    manager: FileEditorManagerEx,
    file: AgentChatVirtualFile,
  ): Int {
    return agentChatEditors(manager = manager, file = file)
      .onEach { editor ->
        editor.showComponentForTests()
        editor.selectNotify()
      }
      .size
  }

  private fun agentChatEditors(
    manager: FileEditorManagerEx,
    file: AgentChatVirtualFile,
  ): List<AgentChatFileEditor> {
    return manager.getAllEditors(file).filterIsInstance<AgentChatFileEditor>()
  }
}

data class RecordingTerminalSentText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

data class RecordingAgentChatOpenedFileSnapshot(
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  @JvmField val initialMessageDispatchStepIndex: Int,
  @JvmField val initialMessageSent: Boolean,
)

private class RecordingAgentChatFileEditorFactory(
  private val terminalTabs: RecordingAgentChatTerminalTabs,
  private val recordSnapshot: (AgentChatTabSnapshot) -> Unit,
) : AgentChatFileEditorFactory {
  override fun create(project: Project, file: AgentChatVirtualFile, editorCoroutineScope: CoroutineScope?): AgentChatFileEditor {
    return AgentChatFileEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      tabSnapshotWriter = AgentChatTabSnapshotWriter { snapshot ->
        recordSnapshot(snapshot)
        project.service<AgentChatTabsService>().upsert(snapshot)
      },
      editorCoroutineScope = editorCoroutineScope,
    ).also { editor ->
      editor.showComponentForTests()
    }
  }
}

private class RecordingAgentChatTerminalTabs : AgentChatTerminalTabs {
  val createCalls: AtomicInteger = AtomicInteger()
  val createCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)
  val startupLaunchSpecs: CopyOnWriteArrayList<AgentSessionTerminalLaunchSpec> = CopyOnWriteArrayList()
  val tab: RecordingAgentChatTerminalTab = RecordingAgentChatTerminalTab()

  override fun createTab(
    project: Project,
    file: AgentChatVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentChatTerminalTab {
    startupLaunchSpecs += startupLaunchSpec
    createCallsFlow.value = createCalls.incrementAndGet()
    return tab
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    tab.coroutineScope.cancel()
  }
}

private class RecordingAgentChatTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")

  @Suppress("RAW_SCOPE_CREATION")
  override val coroutineScope: CoroutineScope = CoroutineScope(Job())
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()

  val sentTexts: CopyOnWriteArrayList<RecordingTerminalSentText> = CopyOnWriteArrayList()
  val sentTextsFlow: MutableStateFlow<List<RecordingTerminalSentText>> = MutableStateFlow(emptyList())
  val backTabCalls: AtomicInteger = AtomicInteger()
  val backTabCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)

  @Volatile
  private var recentOutputTail: String = ""

  private val outputVersion: AtomicLong = AtomicLong()
  private val outputChunks: CopyOnWriteArrayList<RecordingTerminalOutputChunk> = CopyOnWriteArrayList()

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    val version = outputVersion.get()
    return AgentChatTerminalOutputCheckpoint(regularEndOffset = version, alternativeEndOffset = version)
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
      delay(pollIntervalMs.milliseconds)
    }
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness {
    return if (sessionState.value == TerminalViewSessionState.Terminated) {
      AgentChatTerminalInputReadiness.TERMINATED
    }
    else {
      AgentChatTerminalInputReadiness.READY
    }
  }

  override suspend fun readRecentOutputTail(): String = recentOutputTail

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += RecordingTerminalSentText(text, shouldExecute, useBracketedPasteMode)
    sentTextsFlow.value = sentTexts.toList()
  }

  override fun sendBackTab(): Boolean {
    backTabCallsFlow.value = backTabCalls.incrementAndGet()
    emitPlanModeOutput()
    return true
  }

  private fun emitPlanModeOutput() {
    recentOutputTail = "Plan mode"
    val nextVersion = outputVersion.incrementAndGet()
    outputChunks += RecordingTerminalOutputChunk(version = nextVersion, text = recentOutputTail)
  }

  private fun readOutputSince(checkpoint: AgentChatTerminalOutputCheckpoint): String {
    val checkpointVersion = maxOf(checkpoint.regularEndOffset, checkpoint.alternativeEndOffset)
    return outputChunks
      .asSequence()
      .filter { chunk -> chunk.version > checkpointVersion }
      .joinToString(separator = "\n") { chunk -> chunk.text }
  }
}

private data class RecordingTerminalOutputChunk(
  @JvmField val version: Long,
  @JvmField val text: String,
)

private fun recordingSnapshot(snapshot: AgentChatTabSnapshot): RecordingAgentChatOpenedFileSnapshot {
  return RecordingAgentChatOpenedFileSnapshot(
    projectPath = snapshot.identity.projectPath,
    threadIdentity = snapshot.identity.threadIdentity,
    threadId = snapshot.runtime.threadId,
    threadTitle = snapshot.runtime.threadTitle,
    initialMessageDispatchStepIndex = snapshot.runtime.initialMessageDispatchStepIndex,
    initialMessageSent = snapshot.runtime.initialMessageSent,
  )
}

private const val TERMINAL_HARNESS_WAIT_TIMEOUT_MS: Long = 30_000
