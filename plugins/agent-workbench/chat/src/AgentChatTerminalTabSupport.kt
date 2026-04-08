// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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

internal interface AgentChatTerminalTab {
  val component: JComponent
  val preferredFocusableComponent: JComponent
  val coroutineScope: CoroutineScope
  val sessionState: StateFlow<TerminalViewSessionState>
  val keyEventsFlow: Flow<TerminalKeyEvent>
  val terminalView: TerminalView?
    get() = null

  suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint

  suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation

  suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint? = null,
  ): AgentChatTerminalInputReadiness

  suspend fun readRecentOutputTail(): String

  fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean = true)
}

internal data class AgentChatTerminalOutputCheckpoint(
  @JvmField val regularEndOffset: Long,
  @JvmField val alternativeEndOffset: Long,
)

internal data class AgentChatTerminalOutputObservation(
  @JvmField val readiness: AgentChatTerminalInputReadiness,
  @JvmField val text: String,
)

internal enum class AgentChatTerminalInputReadiness {
  READY,
  TIMEOUT,
  TERMINATED,
}

internal interface AgentChatTerminalTabs {
  fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab

  fun closeTab(project: Project, tab: AgentChatTerminalTab)
}

internal object ToolWindowAgentChatTerminalTabs : AgentChatTerminalTabs {
  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    val startupLaunchSpec = file.consumeStartupLaunchSpec()
    val terminalTab = configureAgentChatTerminalTabBuilder(
      builder = TerminalToolWindowTabsManager.getInstance(project).createTabBuilder(),
      file = file,
      startupLaunchSpec = startupLaunchSpec,
    )
      .createTab()
    return ToolWindowAgentChatTerminalTab(
      delegate = terminalTab,
      projectPath = file.projectPath,
      provider = file.provider,
    )
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    val toolWindowTab = (tab as? ToolWindowAgentChatTerminalTab)?.delegate ?: return
    closeTerminalToolWindowTab(project, toolWindowTab)
  }
}

internal fun configureAgentChatTerminalTabBuilder(
  builder: TerminalToolWindowTabBuilder,
  file: AgentChatVirtualFile,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec,
): TerminalToolWindowTabBuilder {
  val projectPath = file.projectPath.takeIf { it.isNotBlank() }
  return builder
    .shouldAddToToolWindow(false)
    .deferSessionStartUntilUiShown(true)
    .workingDirectory(projectPath)
    .sourceNavigationProjectPath(projectPath)
    .processType(TerminalProcessType.NON_SHELL)
    .tabName(file.threadTitle)
    .shellCommand(startupLaunchSpec.command)
    .envVariables(startupLaunchSpec.envVariables)
}

internal fun closeTerminalToolWindowTab(
  project: Project,
  tab: TerminalToolWindowTab,
  managerProvider: (Project) -> TerminalToolWindowTabsManager = TerminalToolWindowTabsManager::getInstance,
) {
  val content = tab.content
  if (content.manager != null) {
    managerProvider(project).closeTab(tab)
  }
  else {
    content.release()
  }
}

private class ToolWindowAgentChatTerminalTab(
  val delegate: TerminalToolWindowTab,
  private val projectPath: String,
  private val provider: AgentSessionProvider?,
) : AgentChatTerminalTab {
  override val component: JComponent
    get() = delegate.content.component

  override val preferredFocusableComponent: JComponent
    get() = delegate.view.preferredFocusableComponent

  override val coroutineScope: CoroutineScope
    get() = delegate.view.coroutineScope

  override val sessionState: StateFlow<TerminalViewSessionState>
    get() = delegate.view.sessionState

  override val keyEventsFlow: Flow<TerminalKeyEvent>
    get() = delegate.view.keyEventsFlow

  override val terminalView: TerminalView
    get() = delegate.view

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness {
    val outputModels = delegate.view.outputModels
    return awaitTerminalInitialMessageReadiness(
      sessionState = delegate.view.sessionState,
      regularOutputModel = outputModels.regular,
      alternativeOutputModel = outputModels.alternative,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      checkpoint = checkpoint,
      onMeaningfulOutput = {
        if (provider != null && AgentSessionProviders.find(provider)?.emitsScopedRefreshSignals == true) {
          notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = projectPath)
        }
      },
    )
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    val outputModels = delegate.view.outputModels
    return withContext(Dispatchers.UI) {
      AgentChatTerminalOutputCheckpoint(
        regularEndOffset = outputModels.regular.endOffset.toAbsolute(),
        alternativeEndOffset = outputModels.alternative.endOffset.toAbsolute(),
      )
    }
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation {
    val outputModels = delegate.view.outputModels
    return awaitTerminalOutputObservation(
      sessionState = delegate.view.sessionState,
      regularOutputModel = outputModels.regular,
      alternativeOutputModel = outputModels.alternative,
      checkpoint = checkpoint,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      onMeaningfulOutput = {
        if (provider != null && AgentSessionProviders.find(provider)?.emitsScopedRefreshSignals == true) {
          notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = projectPath)
        }
      },
    )
  }

  override suspend fun readRecentOutputTail(): String {
    val outputModels = delegate.view.outputModels
    return withContext(Dispatchers.UI) {
      readRecentTerminalOutputTail(outputModels.regular, outputModels.alternative)
    }
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    val sendTextBuilder = delegate.view.createSendTextBuilder()
    if (useBracketedPasteMode) {
      sendTextBuilder.useBracketedPasteMode()
    }
    if (shouldExecute) {
      sendTextBuilder.shouldExecute()
    }
    sendTextBuilder.send(normalizedText)
  }
}

internal class AgentChatTerminalCommandTracker {
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

@OptIn(FlowPreview::class)
internal suspend fun awaitTerminalInitialMessageReadiness(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  timeoutMs: Long,
  idleMs: Long,
  checkpoint: AgentChatTerminalOutputCheckpoint? = null,
  onMeaningfulOutput: () -> Unit = {},
): AgentChatTerminalInputReadiness {
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
  checkpoint: AgentChatTerminalOutputCheckpoint,
  timeoutMs: Long,
  idleMs: Long,
  onMeaningfulOutput: () -> Unit = {},
): AgentChatTerminalOutputObservation {
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
  return AgentChatTerminalOutputObservation(readiness = readiness, text = text)
}

@OptIn(FlowPreview::class)
private suspend fun awaitTerminalOutputReadiness(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  timeoutMs: Long,
  idleMs: Long,
  onMeaningfulOutput: () -> Unit,
  checkpoint: AgentChatTerminalOutputCheckpoint? = null,
): AgentChatTerminalInputReadiness {
  if (sessionState.value == TerminalViewSessionState.Terminated) {
    return AgentChatTerminalInputReadiness.TERMINATED
  }

  val readinessFlow = merge(
    meaningfulTerminalOutputFlow(
      regularOutputModel = regularOutputModel,
      alternativeOutputModel = alternativeOutputModel,
      onMeaningfulOutput = onMeaningfulOutput,
      checkpoint = checkpoint,
    )
      .debounce(idleMs.milliseconds)
      .map { AgentChatTerminalInputReadiness.READY },
    sessionState
      .filter { it == TerminalViewSessionState.Terminated }
      .map { AgentChatTerminalInputReadiness.TERMINATED },
  )

  return withTimeoutOrNull(timeoutMs.milliseconds) {
    readinessFlow.first()
  } ?: AgentChatTerminalInputReadiness.TIMEOUT
}

private fun meaningfulTerminalOutputFlow(
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  onMeaningfulOutput: () -> Unit,
  checkpoint: AgentChatTerminalOutputCheckpoint? = null,
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
  checkpoint: AgentChatTerminalOutputCheckpoint,
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
