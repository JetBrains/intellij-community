// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.XLineBreakpointInfo
import com.intellij.xdebugger.impl.rpc.toTextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FrontendXLineBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  override val type: XLineBreakpointTypeProxy,
  manager: XBreakpointManagerProxy,
  onBreakpointChange: (XBreakpointProxy) -> Unit,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, onBreakpointChange), XLineBreakpointProxy {
  private var lineSourcePosition: XSourcePosition? = null

  private val visualRepresentation = XBreakpointVisualRepresentation(this, useFeLineBreakpointProxy(), manager)

  private val lineBreakpointInfo: XLineBreakpointInfo
    get() = _state.value.lineBreakpointInfo!!

  override fun isTemporary(): Boolean {
    return lineBreakpointInfo.isTemporary
  }

  override fun setTemporary(isTemporary: Boolean) {
    updateLineBreakpointState { it.copy(isTemporary = isTemporary) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setTemporary(id, isTemporary)
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
    if (getFileUrl() != url) {
      val oldFile = getFile()
      updateLineBreakpointState { it.copy(fileUrl = url) }
      lineSourcePosition = null
      visualRepresentation.removeHighlighter()
      visualRepresentation.redrawInlineInlays(oldFile, getLine())
      visualRepresentation.redrawInlineInlays(getFile(), getLine())
      onBreakpointChange()

      project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
        XBreakpointApi.getInstance().setFileUrl(id, url)
      }
    }
  }

  override fun setLine(line: Int) {
    return setLine(line, true)
  }

  fun setLine(line: Int, visualLineMightBeChanged: Boolean) {
    if (getLine() != line) {
      // TODO IJPL-185322 support type.lineShouldBeChanged()
      val oldLine = getLine()
      updateLineBreakpointState { it.copy(line = line) }
      lineSourcePosition = null
      if (visualLineMightBeChanged) {
        visualRepresentation.removeHighlighter()
      }

      // We try to redraw inlays every time,
      // due to lack of synchronization between inlay redrawing and breakpoint changes.
      visualRepresentation.redrawInlineInlays(getFile(), oldLine)
      visualRepresentation.redrawInlineInlays(getFile(), line)

      onBreakpointChange()

      project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
        XBreakpointApi.getInstance().setLine(id, line)
      }
    }
  }

  override fun getHighlightRange(): TextRange? {
    return lineBreakpointInfo.highlightingRange?.toTextRange()
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
    if (useFeLineBreakpointProxy()) {
      visualRepresentation.doUpdateUI(callOnUpdate)
    }
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    return visualRepresentation.highlighter?.gutterIconRenderer
  }

  private fun updateLineBreakpointState(update: (XLineBreakpointInfo) -> XLineBreakpointInfo) {
    _state.update { it.copy(lineBreakpointInfo = update(it.lineBreakpointInfo!!)) }
  }

  override fun createBreakpointDraggableObject(): GutterDraggableObject? {
    return visualRepresentation.createBreakpointDraggableObject()
  }
}