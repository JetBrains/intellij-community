// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

internal class XDebuggerLineChangeHandler(
  scope: CoroutineScope,
  private val handler: (EditorGutterComponentEx, XSourcePositionImpl, types: List<XLineBreakpointType<*>>) -> Unit
) {
  private val lineChangedEvents = MutableSharedFlow<LineChangedEvent?>(extraBufferCapacity = 1,
                                                                       onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      lineChangedEvents.collectLatest { event ->
        if (event == null) {
          return@collectLatest
        }
        val (editor, position) = event
        try {
          val types: List<XLineBreakpointType<*>> = readAction {
            blockingContextToIndicator {
              XBreakpointUtil.getAvailableLineBreakpointTypes(editor.project!!, position, editor)
            }
          }
          withContext(Dispatchers.Main) {
            handler(editor.gutter as EditorGutterComponentEx, position, types)
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          thisLogger().error(e)
        }
      }
    }
  }

  fun lineChanged(editor: Editor, position: XSourcePositionImpl) {
    lineChangedEvents.tryEmit(LineChangedEvent(editor, position))
  }

  fun exitedGutter() {
    lineChangedEvents.tryEmit(null)
  }

  private data class LineChangedEvent(val editor: Editor, val position: XSourcePositionImpl)
}