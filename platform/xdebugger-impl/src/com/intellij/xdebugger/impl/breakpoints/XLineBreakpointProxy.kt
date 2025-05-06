// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLineBreakpointProxy : XBreakpointProxy, XLightLineBreakpointProxy {
  override val type: XLineBreakpointTypeProxy

  fun isTemporary(): Boolean
  fun setTemporary(isTemporary: Boolean)

  fun setFileUrl(url: String)
  fun getFileUrl(): String
  fun setLine(line: Int)

  fun updatePosition()

  fun getHighlighter(): RangeHighlighter?


  @Suppress("DEPRECATION")
  class Monolith @Deprecated("Use breakpoint.asProxy() instead") internal constructor(
    lineBreakpoint: XLineBreakpointImpl<*>,
  ) : XBreakpointProxy.Monolith(lineBreakpoint), XLineBreakpointProxy {
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

    override fun getHighlightRange(): TextRange? {
      return breakpoint.highlightRange
    }

    override fun updatePosition() {
      breakpoint.updatePosition()
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
  }
}