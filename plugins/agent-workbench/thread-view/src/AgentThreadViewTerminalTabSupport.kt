// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

internal interface AgentThreadViewTerminalTab : AgentThreadViewBehaviorTerminalTab {
  val component: JComponent
  val preferredFocusableComponent: JComponent
  val coroutineScope: CoroutineScope
  val sessionState: StateFlow<TerminalViewSessionState>
  val keyEventsFlow: Flow<TerminalKeyEvent>
  val terminalView: TerminalView?
    get() = null

  suspend fun captureOutputCheckpoint(): AgentThreadViewTerminalOutputCheckpoint

  suspend fun awaitOutputObservation(
    checkpoint: AgentThreadViewTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentThreadViewTerminalOutputObservation

  suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentThreadViewTerminalOutputCheckpoint? = null,
  ): AgentThreadViewTerminalInputReadiness

  suspend fun awaitTerminalTitleThreadId(
    provider: AgentSessionProvider?,
    expectedThreadId: String,
    timeoutMs: Long,
  ): AgentThreadViewTerminalInputReadiness {
    return awaitAgentThreadViewTerminalTitleThreadId(provider, expectedThreadId, timeoutMs)
  }

  override suspend fun readRecentOutputTail(): String

  fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean = true)

  suspend fun sendInitialMessageText(
    text: String,
    shouldExecute: Boolean,
    useBracketedPasteMode: Boolean = true,
  ) {
    sendText(text, shouldExecute, useBracketedPasteMode)
  }

  fun sendPendingContextAndExecute(text: String): AgentThreadViewPendingContextSubmissionResult {
    if (text.isEmpty() || sessionState.value != TerminalViewSessionState.Running) {
      return AgentThreadViewPendingContextSubmissionResult.UNAVAILABLE
    }

    val sendTextBuilder = terminalView?.createSendTextBuilder() ?: return AgentThreadViewPendingContextSubmissionResult.UNAVAILABLE
    val submitted = sendTextBuilder
      .sendEndKeyBeforeText()
      .requireBracketedPasteMode()
      .shouldExecute()
      .trySend(text)
    return if (submitted) AgentThreadViewPendingContextSubmissionResult.SUBMITTED else AgentThreadViewPendingContextSubmissionResult.UNAVAILABLE
  }

  fun addInputInterceptor(parentDisposable: Disposable, interceptor: TerminalInputInterceptor): Boolean {
    val view = terminalView ?: return false
    view.addInputInterceptor(parentDisposable, interceptor)
    return true
  }
}

internal data class AgentThreadViewTerminalOutputCheckpoint(
  @JvmField val regularEndOffset: Long,
  @JvmField val alternativeEndOffset: Long,
)

internal data class AgentThreadViewTerminalOutputObservation(
  @JvmField val readiness: AgentThreadViewTerminalInputReadiness,
  @JvmField val text: String,
)

internal enum class AgentThreadViewTerminalInputReadiness {
  READY,
  TIMEOUT,
  TERMINATED,
}

internal enum class AgentThreadViewPendingContextSubmissionResult {
  SUBMITTED,
  UNAVAILABLE,
}

internal interface AgentThreadViewTerminalTabs {
  fun createTab(project: Project, file: AgentThreadViewVirtualFile, startupLaunchSpec: AgentSessionTerminalLaunchSpec): AgentThreadViewTerminalTab

  fun closeTab(project: Project, tab: AgentThreadViewTerminalTab)
}

internal object ToolWindowAgentThreadViewTerminalTabs : AgentThreadViewTerminalTabs {
  override fun createTab(
    project: Project,
    file: AgentThreadViewVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    val terminalToolWindowTabsManager = TerminalToolWindowTabsManager.getInstance(project)
    val terminalTab = configureAgentThreadViewTerminalTabBuilder(
      builder = terminalToolWindowTabsManager.createTabBuilder(),
      file = file,
      startupLaunchSpec = startupLaunchSpec,
    )
      .createTab()
    return ToolWindowAgentThreadViewTerminalTab(
      terminalView = terminalToolWindowTabsManager.detachTab(terminalTab),
      projectPath = file.projectPath,
      provider = file.provider,
      threadId = resolveAgentThreadViewScopedRefreshThreadId(file),
    )
  }

  override fun closeTab(project: Project, tab: AgentThreadViewTerminalTab) {
    tab.terminalView?.coroutineScope?.cancel()
  }
}

internal fun configureAgentThreadViewTerminalTabBuilder(
  builder: TerminalToolWindowTabBuilder,
  file: AgentThreadViewVirtualFile,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec,
): TerminalToolWindowTabBuilder {
  val projectPath = file.projectPath.takeIf { it.isNotBlank() }
  val workingDirectory = startupLaunchSpec.workingDirectory?.takeIf { it.isNotBlank() } ?: projectPath
  val configuredBuilder = builder
    .shouldAddToToolWindow(false)
    .deferSessionStartUntilUiShown(true)
    .workingDirectory(workingDirectory)
    .sourceNavigationProjectPath(projectPath)
    .tabName(file.threadTitle)
    .envVariables(startupLaunchSpec.envVariables)
  if (startupLaunchSpec.useTerminalDefaultShell) {
    return configuredBuilder
  }
  return configuredBuilder
    .processType(TerminalProcessType.NON_SHELL)
    .shellCommand(startupLaunchSpec.command)
}

private class ToolWindowAgentThreadViewTerminalTab(
  override val terminalView: TerminalView,
  private val projectPath: String,
  private val provider: AgentSessionProvider?,
  private val threadId: String?,
) : AgentThreadViewTerminalTab {
  override val component: JComponent
    get() = terminalView.component

  override val preferredFocusableComponent: JComponent
    get() = terminalView.preferredFocusableComponent

  override val coroutineScope: CoroutineScope
    get() = terminalView.coroutineScope

  override val sessionState: StateFlow<TerminalViewSessionState>
    get() = terminalView.sessionState

  override val keyEventsFlow: Flow<TerminalKeyEvent>
    get() = terminalView.keyEventsFlow

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentThreadViewTerminalOutputCheckpoint?,
  ): AgentThreadViewTerminalInputReadiness {
    val outputModels = terminalView.outputModels
    return awaitTerminalInitialMessageReadiness(
      sessionState = terminalView.sessionState,
      regularOutputModel = outputModels.regular,
      alternativeOutputModel = outputModels.alternative,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      checkpoint = checkpoint,
      onMeaningfulOutput = {
        if (provider != null && AgentSessionProviders.find(provider)?.emitsScopedRefreshSignals == true) {
          notifyAgentThreadViewScopedRefresh(
            provider = provider,
            projectPath = projectPath,
            threadId = threadId,
            activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING),
          )
        }
      },
    )
  }

  override suspend fun captureOutputCheckpoint(): AgentThreadViewTerminalOutputCheckpoint {
    val outputModels = terminalView.outputModels
    return withContext(Dispatchers.UI) {
      AgentThreadViewTerminalOutputCheckpoint(
        regularEndOffset = outputModels.regular.endOffset.toAbsolute(),
        alternativeEndOffset = outputModels.alternative.endOffset.toAbsolute(),
      )
    }
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentThreadViewTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentThreadViewTerminalOutputObservation {
    val outputModels = terminalView.outputModels
    return awaitTerminalOutputObservation(
      sessionState = terminalView.sessionState,
      regularOutputModel = outputModels.regular,
      alternativeOutputModel = outputModels.alternative,
      checkpoint = checkpoint,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      onMeaningfulOutput = {
        if (provider != null && AgentSessionProviders.find(provider)?.emitsScopedRefreshSignals == true) {
          notifyAgentThreadViewScopedRefresh(
            provider = provider,
            projectPath = projectPath,
            threadId = threadId,
            activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING),
          )
        }
      },
    )
  }

  override suspend fun readRecentOutputTail(): String {
    val outputModels = terminalView.outputModels
    return withContext(Dispatchers.UI) {
      readRecentTerminalOutputTail(outputModels.regular, outputModels.alternative)
    }
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    sendNormalizedText(normalizedText, shouldExecute, useBracketedPasteMode)
  }

  override suspend fun sendInitialMessageText(
    text: String,
    shouldExecute: Boolean,
    useBracketedPasteMode: Boolean,
  ) {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    sendNormalizedText(normalizedText, shouldExecute, useBracketedPasteMode)
  }

  private fun sendNormalizedText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    val sendTextBuilder = terminalView.createSendTextBuilder()
    if (useBracketedPasteMode) {
      sendTextBuilder.useBracketedPasteMode()
    }
    if (shouldExecute) {
      sendTextBuilder.shouldExecute()
    }
    sendTextBuilder.send(text)
  }

}

internal class AgentThreadViewTerminalCommandTracker {
  private val lineBuffer = StringBuilder()

  fun record(event: KeyEvent): String? {
    return when (event.id) {
      KeyEvent.KEY_TYPED -> {
        val typedChar = event.keyChar
        if (!typedChar.isISOControl() && typedChar != KeyEvent.CHAR_UNDEFINED) {
          lineBuffer.append(typedChar)
        }
        null
      }

      KeyEvent.KEY_PRESSED -> when (event.keyCode) {
        KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE -> {
          if (lineBuffer.isNotEmpty()) {
            lineBuffer.deleteCharAt(lineBuffer.lastIndex)
          }
          null
        }

        KeyEvent.VK_ESCAPE -> {
          lineBuffer.setLength(0)
          null
        }

        KeyEvent.VK_ENTER -> {
          val command = lineBuffer.toString().trim()
          lineBuffer.setLength(0)
          command
        }

        else -> null
      }

      else -> null
    }
  }
}

internal suspend fun awaitTerminalInitialMessageReadiness(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  timeoutMs: Long,
  idleMs: Long,
  checkpoint: AgentThreadViewTerminalOutputCheckpoint? = null,
  onMeaningfulOutput: () -> Unit = {},
): AgentThreadViewTerminalInputReadiness {
  return awaitTerminalOutputReadiness(
    sessionState = sessionState,
    regularOutputModel = regularOutputModel,
    alternativeOutputModel = alternativeOutputModel,
    timeoutMs = timeoutMs,
    idleMs = idleMs,
    onMeaningfulOutput = onMeaningfulOutput,
    checkpoint = checkpoint,
  )
}

internal suspend fun awaitTerminalOutputObservation(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  checkpoint: AgentThreadViewTerminalOutputCheckpoint,
  timeoutMs: Long,
  idleMs: Long,
  onMeaningfulOutput: () -> Unit = {},
): AgentThreadViewTerminalOutputObservation {
  val readiness = awaitTerminalOutputReadiness(
    sessionState = sessionState,
    regularOutputModel = regularOutputModel,
    alternativeOutputModel = alternativeOutputModel,
    timeoutMs = timeoutMs,
    idleMs = idleMs,
    onMeaningfulOutput = onMeaningfulOutput,
    checkpoint = checkpoint,
  )
  val text = withContext(Dispatchers.UI) {
    readTerminalOutputSince(
      regularOutputModel = regularOutputModel,
      alternativeOutputModel = alternativeOutputModel,
      checkpoint = checkpoint,
    )
  }
  return AgentThreadViewTerminalOutputObservation(readiness = readiness, text = text)
}

@OptIn(FlowPreview::class)
private suspend fun awaitTerminalOutputReadiness(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  timeoutMs: Long,
  idleMs: Long,
  onMeaningfulOutput: () -> Unit,
  checkpoint: AgentThreadViewTerminalOutputCheckpoint? = null,
): AgentThreadViewTerminalInputReadiness {
  if (sessionState.value == TerminalViewSessionState.Terminated) {
    return AgentThreadViewTerminalInputReadiness.TERMINATED
  }

  val readinessFlow = merge(
    meaningfulTerminalOutputFlow(
      regularOutputModel = regularOutputModel,
      alternativeOutputModel = alternativeOutputModel,
      onMeaningfulOutput = onMeaningfulOutput,
      checkpoint = checkpoint,
    )
      .debounce(idleMs.milliseconds)
      .map { AgentThreadViewTerminalInputReadiness.READY },
    sessionState
      .filter { it == TerminalViewSessionState.Terminated }
      .map { AgentThreadViewTerminalInputReadiness.TERMINATED },
  )

  return withTimeoutOrNull(timeoutMs.milliseconds) {
    readinessFlow.first()
  } ?: AgentThreadViewTerminalInputReadiness.TIMEOUT
}

private fun meaningfulTerminalOutputFlow(
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  onMeaningfulOutput: () -> Unit,
  checkpoint: AgentThreadViewTerminalOutputCheckpoint? = null,
): Flow<Unit> = callbackFlow {
  val scope = this
  val outputModels = listOf(regularOutputModel, alternativeOutputModel)

  withContext(Dispatchers.UI) {
    val listenerDisposable = scope.asDisposable()
    val listener = object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!scope.isActive || !isMeaningfulTerminalOutputChange(event)) {
          return
        }

        onMeaningfulOutput()
        scope.trySend(Unit)
      }
    }

    outputModels.forEach { model ->
      model.addListener(listenerDisposable, listener)
    }

    if (
      scope.isActive && outputModels.any { model ->
        hasMeaningfulTerminalOutput(
          model = model,
          checkpointOffset = when (model) {
            regularOutputModel -> checkpoint?.regularEndOffset
            alternativeOutputModel -> checkpoint?.alternativeEndOffset
            else -> null
          },
        )
      }
    ) {
      onMeaningfulOutput()
      scope.trySend(Unit)
    }
  }

  awaitClose()
}

private fun hasMeaningfulTerminalOutput(model: TerminalOutputModel, checkpointOffset: Long? = null): Boolean {
  val end = model.endOffset
  val availableStart = maxTerminalOffset(
    model.startOffset,
    checkpointOffset?.let(TerminalOffset::of) ?: model.startOffset,
  )
  val availableChars = end - availableStart
  if (availableChars <= 0) {
    return false
  }
  val start = if (availableChars > READINESS_SCAN_LIMIT_CHARS) end - READINESS_SCAN_LIMIT_CHARS else availableStart
  return model.getText(start, end).any(::isMeaningfulTerminalOutputChar)
}

private fun readTerminalOutputSince(
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  checkpoint: AgentThreadViewTerminalOutputCheckpoint,
): String {
  return listOf(
    readTerminalOutputChunk(regularOutputModel, checkpoint.regularEndOffset),
    readTerminalOutputChunk(alternativeOutputModel, checkpoint.alternativeEndOffset),
  )
    .filter { it.isNotEmpty() }
    .joinToString(separator = "\n")
}

private fun readRecentTerminalOutputTail(
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
): String {
  return listOf(
    readRecentTerminalOutputChunk(regularOutputModel),
    readRecentTerminalOutputChunk(alternativeOutputModel),
  )
    .filter { it.isNotEmpty() }
    .joinToString(separator = "\n")
}

private fun readTerminalOutputChunk(model: TerminalOutputModel, checkpointOffset: Long): String {
  val end = model.endOffset
  val availableStart = maxTerminalOffset(model.startOffset, TerminalOffset.of(checkpointOffset))
  val availableChars = end - availableStart
  if (availableChars <= 0) {
    return ""
  }
  val boundedStart = if (availableChars > POST_SEND_SCAN_LIMIT_CHARS) end - POST_SEND_SCAN_LIMIT_CHARS else availableStart
  return model.getText(boundedStart, end).toString()
}

private fun readRecentTerminalOutputChunk(model: TerminalOutputModel): String {
  val end = model.endOffset
  val availableChars = end - model.startOffset
  if (availableChars <= 0) {
    return ""
  }
  val start = if (availableChars > TERMINAL_TAIL_SCAN_LIMIT_CHARS) {
    end - TERMINAL_TAIL_SCAN_LIMIT_CHARS
  }
  else {
    model.startOffset
  }
  return model.getText(start, end).toString()
}

private fun maxTerminalOffset(first: TerminalOffset, second: TerminalOffset): TerminalOffset {
  return if (first.toAbsolute() >= second.toAbsolute()) first else second
}

internal fun isMeaningfulTerminalOutputChange(event: TerminalContentChangeEvent): Boolean {
  return !event.isTypeAhead && !event.isTrimming && event.newText.isNotEmpty() && event.newText.any(::isMeaningfulTerminalOutputChar)
}

private fun isMeaningfulTerminalOutputChar(char: Char): Boolean {
  return !char.isWhitespace() && char != '%'
}

internal const val INITIAL_MESSAGE_READINESS_TIMEOUT_MS: Long = 2_000
internal const val INITIAL_MESSAGE_OUTPUT_IDLE_MS: Long = 250
internal const val INITIAL_MESSAGE_POST_SEND_OBSERVATION_TIMEOUT_MS: Long = 1_500
internal const val INITIAL_MESSAGE_POST_SEND_OUTPUT_IDLE_MS: Long = 150

private const val POST_SEND_SCAN_LIMIT_CHARS: Long = 8_192
private const val READINESS_SCAN_LIMIT_CHARS: Long = 8_192
private const val TERMINAL_TAIL_SCAN_LIMIT_CHARS: Long = 4_096
