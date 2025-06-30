// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.debugger.impl.rpc.ValueHintEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ValueLookupManagerController(private val cs: CoroutineScope) {
  private val events = MutableSharedFlow<ValueHintEvent>()
  private val channel = Channel<ValueHintEvent>(Channel.UNLIMITED)

  init {
    cs.launch {
      channel.consumeEach {
        events.emit(it)
      }
    }
  }

  fun getEventsFlow(): Flow<ValueHintEvent> = events.asSharedFlow()

  /**
   * Starts [ValueLookupManager] listening for events (e.g. mouse movement) to trigger evaluation popups
   */
  fun startListening() {
    channel.trySend(ValueHintEvent.StartListening)
  }

  /**
   * Requests [ValueLookupManager] to hide current evaluation hints
   */
  fun hideHint() {
    channel.trySend(ValueHintEvent.HideHint)
  }

  fun showEditorInfoTooltip(event: EditorMouseEvent?) {
    if (event != null) {
      cs.launch(Dispatchers.EDT) {
        hideHint()
        EditorMouseHoverPopupManager.getInstance().showInfoTooltip(event)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ValueLookupManagerController = project.service<ValueLookupManagerController>()

    /**
     * @see XDebuggerUtil#disableValueLookup(Editor)
     */
    @JvmField
    val DISABLE_VALUE_LOOKUP = Key.create<Boolean>("DISABLE_VALUE_LOOKUP");
  }
}