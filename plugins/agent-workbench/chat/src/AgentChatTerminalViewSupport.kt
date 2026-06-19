// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.ide.DataManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.editor.Editor
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AgentChatActiveTerminalSnapshot(
  val model: TerminalOutputModel,
  val snapshot: TerminalOutputModelSnapshot,
)

@ApiStatus.Internal
suspend fun captureActiveTerminalSnapshot(
  terminalView: TerminalView,
  sessionState: StateFlow<TerminalViewSessionState>,
  onUnavailable: () -> Unit,
): AgentChatActiveTerminalSnapshot? {
  return withContext(Dispatchers.UI) {
    if (sessionState.value == TerminalViewSessionState.Terminated) {
      onUnavailable()
      null
    }
    else {
      val editor = resolveTerminalEditor(terminalView)
      if (editor == null || editor.isDisposed) {
        onUnavailable()
        null
      }
      else {
        val model = terminalView.activeOutputModel()
        AgentChatActiveTerminalSnapshot(model = model, snapshot = model.takeSnapshot())
      }
    }
  }
}

@ApiStatus.Internal
fun terminalOutputModelChangeFlow(terminalView: TerminalView): Flow<Unit> = callbackFlow {
  val scope = this
  withContext(Dispatchers.UI) {
    val listenerDisposable = scope.asDisposable()
    val listener = object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!scope.isActive || event.isTypeAhead) {
          return
        }
        scope.trySend(Unit)
      }
    }
    terminalView.outputModels.regular.addListener(listenerDisposable, listener)
    terminalView.outputModels.alternative.addListener(listenerDisposable, listener)
  }
  awaitClose()
}

@ApiStatus.Internal
fun resolveTerminalEditor(terminalView: TerminalView): Editor? {
  val dataContext = DataManager.getInstance().getDataContext(terminalView.component)
  return TerminalActionUtil.EDITOR_KEY.getData(dataContext)
}

@ApiStatus.Internal
fun TerminalOutputModelSnapshot.lineText(line: TerminalLineIndex): String {
  return getText(getStartOfLine(line), getEndOfLine(line)).toString()
}

@ApiStatus.Internal
interface AgentChatDisposableController {
  fun dispose()
}
