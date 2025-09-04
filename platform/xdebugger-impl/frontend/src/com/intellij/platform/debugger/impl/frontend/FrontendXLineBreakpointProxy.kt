// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicReference

internal enum class RegistrationStatus {
  NOT_STARTED, IN_PROGRESS, REGISTERED, DEREGISTERED
}

internal class FrontendXLineBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  override val type: XLineBreakpointTypeProxy,
  manager: FrontendXBreakpointManager,
  onBreakpointChange: (XBreakpointProxy) -> Unit,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, manager.breakpointRequestCounter, onBreakpointChange), XLineBreakpointProxy {
  private var lineSourcePosition: XSourcePosition? = null

  private val visualRepresentation = XBreakpointVisualRepresentation(cs, this, useFeLineBreakpointProxy(), manager)

  private val lineBreakpointInfo: XLineBreakpointInfo
    get() = currentState.lineBreakpointInfo!!

  internal val registrationInLineManagerStatus = AtomicReference(RegistrationStatus.NOT_STARTED)

  override fun isTemporary(): Boolean {
    return lineBreakpointInfo.isTemporary
  }

  override fun setTemporary(isTemporary: Boolean) {
    updateLineBreakpointStateIfNeeded(newValue = isTemporary,
                                      getter = { it.isTemporary },
                                      copy = { it.copy(isTemporary = isTemporary) }) { requestId ->
      XBreakpointApi.getInstance().setTemporary(id, requestId, isTemporary)
    }
  }

  override fun getSourcePosition(): XSourcePosition? {
    if (lineSourcePosition != null) {
      return lineSourcePosition
    }
    lineSourcePosition = super.getSourcePosition()
    if (lineSourcePosition == null) {
      lineSourcePosition = XDebuggerUtil.getInstance().createPosition(getFile(), getLine())
    }
    return lineSourcePosition
  }


  override fun getFile(): VirtualFile? {
    return lineBreakpointInfo.file?.virtualFile()
  }

  override fun getFileUrl(): String {
    return lineBreakpointInfo.fileUrl
  }

  override fun getLine(): Int {
    return lineBreakpointInfo.line
  }

  override fun setFileUrl(url: String) {
    val oldFile = getFile()
    updateLineBreakpointStateIfNeeded(
      newValue = url,
      getter = { it.fileUrl },
      copy = { it.copy(fileUrl = url) },
      afterStateChanged = {
        lineSourcePosition = null
        visualRepresentation.removeHighlighter()
        visualRepresentation.redrawInlineInlays(oldFile, getLine())
        visualRepresentation.redrawInlineInlays(getFile(), getLine())
      }) { requestId ->
      XBreakpointApi.getInstance().setFileUrl(id, requestId, url)
    }
  }

  override fun setLine(line: Int) {
    return setLine(line, visualLineMightBeChanged = true)
  }

  private fun setLine(line: Int, visualLineMightBeChanged: Boolean) {
    val oldLine = getLine()
    if (oldLine != line) {
      // TODO IJPL-185322 support type.lineShouldBeChanged()
      updateLineBreakpointStateIfNeeded(
        newValue = line to lineBreakpointInfo.invalidateHighlightingRangeOrNull(),
        getter = { it.line to it.highlightingRange },
        copy = { it.copy(line = line, highlightingRange = it.invalidateHighlightingRangeOrNull()) },
        afterStateChanged = {
          lineSourcePosition = null
          if (visualLineMightBeChanged) {
            visualRepresentation.removeHighlighter()
          }

          // We try to redraw inlays every time,
          // due to lack of synchronization between inlay redrawing and breakpoint changes.
          visualRepresentation.redrawInlineInlays(getFile(), oldLine)
          visualRepresentation.redrawInlineInlays(getFile(), line)
        }
      ) { requestId ->
        XBreakpointApi.getInstance().setLine(id, requestId, line)
      }
    }
    else {
      updateLineBreakpointStateIfNeeded(
        newValue = lineBreakpointInfo.invalidateHighlightingRangeOrNull(),
        getter = { it.highlightingRange },
        copy = { it.copy(highlightingRange = it.invalidateHighlightingRangeOrNull()) },
        afterStateChanged = {
          // offset in file might change, pass reset to backend
          lineSourcePosition = null
        },
        forceRequestWithoutUpdate = true,
      ) { requestId ->
        XBreakpointApi.getInstance().updatePosition(id, requestId)
      }
    }
  }

  override fun getHighlightRange(): XLineBreakpointHighlighterRange {
    val range = lineBreakpointInfo.highlightingRange
    if (range == UNAVAILABLE_RANGE) return XLineBreakpointHighlighterRange.Unavailable
    return XLineBreakpointHighlighterRange.Available(range?.toTextRange())
  }

  override fun updatePosition() {
    val highlighter: RangeMarker? = visualRepresentation.rangeMarker
    if (highlighter != null && highlighter.isValid()) {
      lineSourcePosition = null // reset the source position even if the line number has not changed, as the offset may be cached inside
      setLine(highlighter.getDocument().getLineNumber(highlighter.getStartOffset()), visualLineMightBeChanged = false)
    }
  }

  override fun getHighlighter(): RangeHighlighter? {
    return visualRepresentation.highlighter
  }

  override fun dispose() {
    super.dispose()
    visualRepresentation.removeHighlighter()
    visualRepresentation.redrawInlineInlays(getFile(), getLine())
  }

  override fun doUpdateUI(callOnUpdate: () -> Unit) {
    visualRepresentation.doUpdateUI(callOnUpdate)
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    return visualRepresentation.highlighter?.gutterIconRenderer
  }

  private fun <T> updateLineBreakpointStateIfNeeded(
    newValue: T,
    getter: (XLineBreakpointInfo) -> T,
    copy: (XLineBreakpointInfo) -> XLineBreakpointInfo,
    afterStateChanged: () -> Unit = {},
    forceRequestWithoutUpdate: Boolean = false,
    sendRequest: suspend (Long) -> Unit,
  ) {
    return updateStateIfNeeded(newValue = newValue,
                               getter = { state -> getter(state.lineBreakpointInfo!!) },
                               copy = { state -> state.copy(lineBreakpointInfo = copy(state.lineBreakpointInfo!!)) },
                               afterStateChanged = afterStateChanged,
                               forceRequestWithoutUpdate = forceRequestWithoutUpdate) { requestId ->
      sendRequest(requestId)
    }
  }

  override fun createBreakpointDraggableObject(): GutterDraggableObject {
    return visualRepresentation.createBreakpointDraggableObject()
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, type=${type.id}, line=${getLine()}, file=${getFileUrl()})"
  }
}

private val UNAVAILABLE_RANGE = XLineBreakpointTextRange(-1, -1)
private fun XLineBreakpointInfo.invalidateHighlightingRangeOrNull() = if (highlightingRange == null) null else UNAVAILABLE_RANGE