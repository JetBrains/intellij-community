// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.impl.breakpoints.XBreakpointVisualRepresentation
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.XLineBreakpointInfo
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
    visualRepresentation.updateUI()
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
    return lineBreakpointInfo.fileUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
  }

  override fun getLine(): Int {
    return lineBreakpointInfo.line
  }

  override fun setFileUrl(url: String) {
    // TODO IJPL-185322 implement like it is done in XLineBreakpointImpl
    // TODO IJPL-185322 send through RPC
    updateLineBreakpointState { it.copy(fileUrl = url) }
    onBreakpointChange()
  }

  override fun setLine(line: Int) {
    // TODO IJPL-185322 implement like it is done in XLineBreakpointImpl
    // TODO IJPL-185322 send through RPC
    updateLineBreakpointState { it.copy(line = line) }
    onBreakpointChange()
  }

  override fun getHighlightRange(): TextRange? {
    // TODO IJPL-185322 implement highlight range through type
    return null
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