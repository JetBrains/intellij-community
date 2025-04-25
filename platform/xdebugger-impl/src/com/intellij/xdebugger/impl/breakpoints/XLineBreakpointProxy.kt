// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLineBreakpointProxy : XBreakpointProxy {
  override val type: XLineBreakpointTypeProxy

  fun isTemporary(): Boolean
  fun setTemporary(isTemporary: Boolean)

  fun getFile(): VirtualFile?
  fun getRangeMarker(): RangeMarker?
  fun getLine(): Int
  fun setFileUrl(url: String)
  fun setLine(line: Int)
  fun setHighlighter(rangeMarker: RangeMarker)
  fun getHighlightRange(): TextRange?
  fun getHighlighter(): RangeHighlighter?
  fun createGutterIconRenderer(): GutterIconRenderer?


  @Suppress("DEPRECATION")
  class Monolith @Deprecated("Use breakpoint.asProxy() instead") internal constructor(
    lineBreakpoint: XLineBreakpointImpl<*>,
  ) : XBreakpointProxy.Monolith(lineBreakpoint), XLineBreakpointProxy {
    override val breakpoint: XLineBreakpointImpl<*> = lineBreakpoint

    override val type: XLineBreakpointTypeProxy = lineBreakpoint.type.asProxy(breakpoint.project)

    private val visualRepresentation: XBreakpointVisualRepresentation = XBreakpointVisualRepresentation(lineBreakpoint)

    override fun createBreakpointDraggableObject(): GutterDraggableObject {
      return visualRepresentation.createBreakpointDraggableObject()
    }

    override fun getFile(): VirtualFile? = breakpoint.file

    override fun getRangeMarker(): RangeMarker? {
      return breakpoint.rangeMarker
    }

    override fun getLine(): Int {
      return breakpoint.line
    }

    override fun setFileUrl(url: String) {
      breakpoint.fileUrl = url
    }

    override fun setLine(line: Int) {
      breakpoint.line = line
    }

    override fun setHighlighter(rangeMarker: RangeMarker) {
      breakpoint.setHighlighter(rangeMarker)
    }

    override fun getHighlightRange(): TextRange? {
      return breakpoint.highlightRange
    }

    override fun getHighlighter(): RangeHighlighter? {
      return breakpoint.highlighter
    }

    override fun createGutterIconRenderer(): GutterIconRenderer? {
      return breakpoint.createGutterIconRenderer()
    }

    override fun isTemporary(): Boolean = breakpoint.isTemporary

    override fun setTemporary(isTemporary: Boolean) {
      breakpoint.isTemporary = isTemporary
    }
  }
}