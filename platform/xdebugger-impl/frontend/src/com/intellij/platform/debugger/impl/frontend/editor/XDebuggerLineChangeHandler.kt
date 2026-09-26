// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.editor

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.BreakpointArea
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendEditorLinesBreakpointsInfoManager
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.XSourcePositionImpl
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
  private val handler: (EditorGutterComponentEx, XSourcePositionImpl, BreakpointTypeSuggestion?, BreakpointArea, GutterHoverModifiers) -> Unit,
) {
  private val lineChangedEvents = MutableSharedFlow<LineChangedEvent?>(extraBufferCapacity = 1,
                                                                       onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      lineChangedEvents.collectLatest { event ->
        if (event == null) {
          return@collectLatest
        }
        val (editor, position, breakpointArea, modifiers) = event
        val project = editor.project ?: return@collectLatest
        try {
          val suggestion = getBreakpointTypeSuggestion(project, editor, position.line, breakpointArea)
          withContext(Dispatchers.Main) {
            handler(editor.gutter as EditorGutterComponentEx, position, suggestion, breakpointArea, modifiers)
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

  fun lineChanged(editor: Editor, position: XSourcePositionImpl, breakpointArea: BreakpointArea, modifiers: GutterHoverModifiers) {
    lineChangedEvents.tryEmit(LineChangedEvent(editor, position, breakpointArea, modifiers))
  }

  fun exitedGutter() {
    lineChangedEvents.tryEmit(null)
  }

  private suspend fun getBreakpointTypeSuggestion(
    project: Project,
    editor: Editor,
    line: Int,
    breakpointArea: BreakpointArea,
  ): BreakpointTypeSuggestion? {
    val types: List<XBreakpointTypeProxy> =
      FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLine(editor, line).types
    // Apply the same rule as XToggleLineBreakpointActionHandler.isPlacementAvailable, which the click uses.
    // A line can support more than one type, and one supported type is enough for the inter-line placement
    val useInterLinePlacement = breakpointArea is BreakpointArea.InterLine &&
                                types.any { it is XLineBreakpointTypeProxy && it.supportsInterLinePlacement() }
    return types.firstOrNull()?.let { BreakpointTypeSuggestion(it, useInterLinePlacement) }
  }

  private data class LineChangedEvent(
    val editor: Editor,
    val position: XSourcePositionImpl,
    val breakpointArea: BreakpointArea,
    val modifiers: GutterHoverModifiers,
  )
}

internal data class BreakpointTypeSuggestion(
  val breakpointType: XBreakpointTypeProxy,
  val useInterLinePlacement: Boolean,
)

internal data class GutterHoverModifiers(
  val isShiftDown: Boolean,
  val isAltDown: Boolean,
)
