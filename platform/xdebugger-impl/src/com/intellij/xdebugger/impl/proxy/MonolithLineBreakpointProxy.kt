// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointAttachment
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.breakpoints.highlightRange

@Suppress("DEPRECATION")
internal class MonolithLineBreakpointProxy @Deprecated("Use breakpoint.asProxy() instead") internal constructor(
  lineBreakpoint: XLineBreakpointImpl<*>,
) : MonolithBreakpointProxy(lineBreakpoint), XLineBreakpointProxy {
  override val breakpoint: XLineBreakpointImpl<*> = lineBreakpoint

  override val type: XLineBreakpointTypeProxy get() = breakpoint.type.asProxy(breakpoint.project)

  override fun createBreakpointDraggableObject(): GutterDraggableObject {
    return breakpoint.createBreakpointDraggableObject()
  }

  override fun getFile(): VirtualFile? = breakpoint.file

  override fun getLine(): Int {
    return breakpoint.line
  }

  override fun setFileUrl(url: String) {
    breakpoint.fileUrl = url
  }

  override fun getFileUrl(): String = breakpoint.fileUrl

  override fun setLine(line: Int) {
    breakpoint.line = line
  }

  override fun getHighlightRange(): XLineBreakpointHighlighterRange {
    val range = runReadAction { breakpoint.highlightRange }
    return XLineBreakpointHighlighterRange.Available(range)
  }

  override suspend fun getHighlightRangeSuspend(): XLineBreakpointHighlighterRange {
    val range = readAction { breakpoint.highlightRange }
    return XLineBreakpointHighlighterRange.Available(range)
  }

  override fun updatePosition() {
    breakpoint.updatePosition()
  }

  override fun fastUpdatePosition() {
    // do nothing
  }

  override fun getHighlighter(): RangeHighlighter? {
    return breakpoint.highlighter
  }

  override fun doUpdateUI(callOnUpdate: () -> Unit) {
    breakpoint.doUpdateUI(callOnUpdate)
  }

  override fun isTemporary(): Boolean = breakpoint.isTemporary

  override fun setTemporary(isTemporary: Boolean) {
    breakpoint.isTemporary = isTemporary
  }

  override fun updateIcon() {
    breakpoint.clearIcon()
  }

  override val attachments: List<XBreakpointAttachment>
    get() = emptyList()
}