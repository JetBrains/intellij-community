// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.editor

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendEditorLinesBreakpointsInfoManager
import com.intellij.platform.debugger.impl.frontend.getAvailableBreakpointTypesFromServer
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import javax.swing.Icon

internal class XDebuggerLineChangeHandler(
  scope: CoroutineScope,
  private val handler: (EditorGutterComponentEx, XSourcePositionImpl, icon: Icon?) -> Unit
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
        val project = editor.project ?: return@collectLatest
        try {
          val type = getBreakpointTypeForLine(project, editor, position.line)
          val icon = type?.enabledIcon
          withContext(Dispatchers.Main) {
            handler(editor.gutter as EditorGutterComponentEx, position, icon)
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

  private suspend fun getBreakpointTypeForLine(project: Project, editor: Editor, line: Int): XBreakpointTypeProxy? {
    val types: List<XBreakpointTypeProxy> = if (useFeProxy()) {
      FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLine(editor, line).types
    }
    else {
      getAvailableBreakpointTypesFromServer(project, editor, line).types
    }
    return types.firstOrNull()
  }

  private data class LineChangedEvent(val editor: Editor, val position: XSourcePositionImpl)
}