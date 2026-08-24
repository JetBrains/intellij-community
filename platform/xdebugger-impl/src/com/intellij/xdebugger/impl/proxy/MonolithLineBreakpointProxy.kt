// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.breakpoints.highlightRange

@Suppress("DEPRECATION")
internal class MonolithLineBreakpointProxy @Deprecated("Use breakpoint.asProxy() instead") internal constructor(
  lineBreakpoint: XLineBreakpointImpl<*>,
) : MonolithBreakpointProxy(lineBreakpoint), XLineBreakpointProxy {
  override val breakpoint: XLineBreakpointImpl<*> = lineBreakpoint

  override val type: XLineBreakpointTypeProxy get() = breakpoint.type.asProxy(breakpoint.project)

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

  override fun setPlacement(placement: XLineBreakpointVerticalPlacement) {
    breakpoint.placement = placement
  }

  override fun getPlacement(): XLineBreakpointVerticalPlacement = breakpoint.placement

  override fun getHighlightRange(): XLineBreakpointHighlighterRange {
    val range = runReadActionBlocking { breakpoint.highlightRange }
    return XLineBreakpointHighlighterRange.Available(range)
  }

  override suspend fun getHighlightRangeSuspend(): XLineBreakpointHighlighterRange {
    val range = readAction { breakpoint.highlightRange }
    return XLineBreakpointHighlighterRange.Available(range)
  }

  override fun updateIcon() {
    breakpoint.clearIcon()
  }
}
