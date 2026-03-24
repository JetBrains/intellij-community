// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.editor

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendEditorLinesBreakpointsInfoManager
import com.intellij.platform.debugger.impl.frontend.getAvailableBreakpointTypesFromServer
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.openapi.editor.impl.BreakpointArea
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class XDebuggerLineChangeHandler(
  scope: CoroutineScope,
  private val handler: (EditorGutterComponentEx, XSourcePositionImpl, breakpointType: XBreakpointTypeProxy?, BreakpointArea) -> Unit
) {
  private val lineChangedEvents = MutableSharedFlow<LineChangedEvent?>(extraBufferCapacity = 1,
                                                                       onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      lineChangedEvents.collectLatest { event ->
        if (event == null) {
          return@collectLatest
        }
        val (editor, position, breakpointArea) = event
        val project = editor.project ?: return@collectLatest
        try {
          val type = getBreakpointTypeForLine(project, editor, position.line, breakpointArea)
          withContext(Dispatchers.Main) {
            handler(editor.gutter as EditorGutterComponentEx, position, type, breakpointArea)
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

  fun lineChanged(editor: Editor, position: XSourcePositionImpl, breakpointArea: BreakpointArea) {
    lineChangedEvents.tryEmit(LineChangedEvent(editor, position, breakpointArea))
  }

  fun exitedGutter() {
    lineChangedEvents.tryEmit(null)
  }

  private suspend fun getBreakpointTypeForLine(project: Project, editor: Editor, line: Int, breakpointArea: BreakpointArea): XBreakpointTypeProxy? {
    val types: List<XBreakpointTypeProxy> = if (SplitDebuggerMode.isSplitDebugger()) {
      FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLine(editor, line).types
    }
    else {
      getAvailableBreakpointTypesFromServer(project, editor, line).types
    }
    return if (breakpointArea is BreakpointArea.InterLine) {
      types.singleOrNull()?.takeIf { it is XLineBreakpointTypeProxy && it.supportsInterLinePlacement() }
    } else {
      types.firstOrNull()
    }
  }

  private data class LineChangedEvent(val editor: Editor, val position: XSourcePositionImpl, val breakpointArea: BreakpointArea)
}
