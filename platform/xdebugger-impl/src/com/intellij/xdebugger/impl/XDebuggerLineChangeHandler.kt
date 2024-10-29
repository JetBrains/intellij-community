// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
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
          val icon = project.service<XDebuggerLineChangeIconProvider>().getIcon(position, editor)
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

  private data class LineChangedEvent(val editor: Editor, val position: XSourcePositionImpl)
}

@ApiStatus.Internal
open class XDebuggerLineChangeIconProvider(val project: Project) {
  open suspend fun getIcon(position: XSourcePositionImpl, editor: Editor): Icon? {
    return readAction {
      blockingContextToIndicator {
        val types = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, false, editor)
        types.firstOrNull()?.enabledIcon
      }
    }
  }
}