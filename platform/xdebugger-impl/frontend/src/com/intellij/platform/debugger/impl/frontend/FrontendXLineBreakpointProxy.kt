// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FrontendXLineBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  override val type: XLineBreakpointTypeProxy,
  onBreakpointChange: () -> Unit,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, onBreakpointChange), XLineBreakpointProxy {

  override fun isTemporary(): Boolean {
    return _state.value.isTemporary
  }

  override fun setTemporary(isTemporary: Boolean) {
    _state.update { it.copy(isTemporary = isTemporary) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setTemporary(id, isTemporary)
    }
  }


  override fun getFile(): VirtualFile? {
    // TODO IJPL-185111 pass through line breakpoint info
    return null
  }

  override fun getRangeMarker(): RangeMarker? {
    // TODO IJPL-185111
    return null
  }

  override fun getLine(): Int {
    // TODO IJPL-185111 pass through line breakpoint info
    return 0
  }

  override fun setFileUrl(url: String) {
    // TODO IJPL-185111 implement set file url
  }

  override fun setLine(line: Int) {
    // TODO IJPL-185111 implement set line
  }

  override fun setHighlighter(rangeMarker: RangeMarker) {
    // TODO IJPL-185111 implement set highlighter
  }

  override fun getHighlightRange(): TextRange? {
    // TODO IJPL-185111 implement highlight range
    return null
  }

  override fun getHighlighter(): RangeHighlighter? {
    // TODO IJPL-185111 implement range highlighter
    return null
  }

  override fun createGutterIconRenderer(): GutterIconRenderer? {
    // TODO IJPL-185111 implement create gutter icon renderer
    return null
  }
}