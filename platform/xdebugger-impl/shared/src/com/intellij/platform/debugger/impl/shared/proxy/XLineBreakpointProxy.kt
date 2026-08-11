// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLineBreakpointProxy : XBreakpointProxy, XLightLineBreakpointProxy {
  override val type: XLineBreakpointTypeProxy

  fun isTemporary(): Boolean
  fun setTemporary(isTemporary: Boolean)

  fun setFileUrl(url: String)
  fun getFileUrl(): String
  fun setLine(line: Int)
  fun setPlacement(placement: XLineBreakpointVerticalPlacement)

  override fun getPlacement(): XLineBreakpointVerticalPlacement

}
