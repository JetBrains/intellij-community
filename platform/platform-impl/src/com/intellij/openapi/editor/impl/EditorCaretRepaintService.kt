// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.APP)
internal class EditorCaretRepaintService(coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic fun getInstance(): EditorCaretRepaintService = service()
  }

  var editor: EditorImpl?
    get() = editorFlow.value
    set(value) {
      editorFlow.value = value
    }

  var isBlinking: Boolean
    get() = isBlinkingRef.get()
    set(value) {
      isBlinkingRef.set(value)
    }

  var blinkPeriod: Long
    get() = blinkPeriodRef.get()
    set(value) {
      blinkPeriodRef.set(value.coerceAtLeast(10L))
    }

  private val isBlinkingRef = AtomicBoolean(true)
  private val blinkPeriodRef = AtomicLong(500L)

  private val editorFlow = MutableStateFlow<EditorImpl?>(null)
  private val restartRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      editorFlow.combine(restartRequests) { editor, _ -> editor }.collectLatest { editor ->
        if (editor != null) {
          runCatching {
            blink(editor)
          }.getOrLogException { e ->
            LOG.error("An exception occurred while blinking the active caret", e)
          }
        }
      }
    }
    restart()
  }

  fun restart() {
    check(restartRequests.tryEmit(Unit))
  }

  private suspend fun blink(editor: EditorImpl) {
    while (true) {
      delay(blinkPeriod)
      val cursor = editor.myCaretCursor
      val time = System.currentTimeMillis() - cursor.startTime
      if (time > blinkPeriod) {
        var toRepaint = true
        if (isBlinking) {
          cursor.isActive = !cursor.isActive
        }
        else {
          toRepaint = !cursor.isActive
          cursor.isActive = true
        }
        if (toRepaint) {
          cursor.repaint()
        }
      }
    }
  }
}

private val LOG = logger<EditorCaretRepaintService>()
