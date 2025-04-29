// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.impl.breakpoints.XBreakpointVisualRepresentation
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.XLineBreakpointInfo
import com.intellij.xdebugger.impl.rpc.toTextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FrontendXLineBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  override val type: XLineBreakpointTypeProxy,
  onBreakpointChange: () -> Unit,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, onBreakpointChange), XLineBreakpointProxy {

  private val visualRepresentation = XBreakpointVisualRepresentation(
    this,
    useFeLineBreakpointProxy(),
  ) { callback ->
    FrontendXLineBreakpointUpdatesManager.getInstance(project).queueBreakpointUpdate(this@FrontendXLineBreakpointProxy) {
      callback.run()
    }
  }

  private val lineBreakpointInfo: XLineBreakpointInfo
    get() = _state.value.lineBreakpointInfo!!

  init {
    cs.launch(Dispatchers.Default) {
      visualRepresentation.updateUI()
    }
  }

  override fun onBreakpointChange() {
    super.onBreakpointChange()
    cs.launch(Dispatchers.Default) {
      visualRepresentation.doUpdateUI {}
    }
  }

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


  override fun getFile(): VirtualFile? {
    return lineBreakpointInfo.file?.virtualFile()
  }

  private fun getFileUrl(): String? {
    return lineBreakpointInfo.fileUrl
  }

  override fun getLine(): Int {
    return lineBreakpointInfo.line
  }

  override fun setFileUrl(url: String) {
    if (getFileUrl() != url) {
      val oldFile = getFile()
      updateLineBreakpointState { it.copy(fileUrl = url) }
      // TODO IJPL-185322 support source position?
      // mySourcePosition = null
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
    if (getLine() != line) {
      // TODO IJPL-185322 support type.lineShouldBeChanged()
      val oldLine = getLine()
      updateLineBreakpointState { it.copy(line = line) }
      // TODO IJPL-185322 support source position?
      // mySourcePosition = null
      visualRepresentation.removeHighlighter()

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

  private fun updateLineBreakpointState(update: (XLineBreakpointInfo) -> XLineBreakpointInfo) {
    _state.update { it.copy(lineBreakpointInfo = update(it.lineBreakpointInfo!!)) }
  }

  override fun createBreakpointDraggableObject(): GutterDraggableObject? {
    return visualRepresentation.createBreakpointDraggableObject()
  }
}