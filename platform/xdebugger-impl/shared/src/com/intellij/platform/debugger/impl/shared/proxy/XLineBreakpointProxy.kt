// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLineBreakpointProxy : XBreakpointProxy, XLightLineBreakpointProxy {
  override val type: XLineBreakpointTypeProxy

  /**
   * IDE components that depend on the breakpoint's current state.
   *
   * @see XBreakpointAttachment
   */
  val attachments: List<XBreakpointAttachment>

  fun isTemporary(): Boolean
  fun setTemporary(isTemporary: Boolean)

  fun setFileUrl(url: String)
  fun getFileUrl(): String
  fun setLine(line: Int)

  fun updatePosition()
  fun fastUpdatePosition()

  fun getHighlighter(): RangeHighlighter?
}