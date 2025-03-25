// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XStackFrameDto
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.sourcePosition

// TODO[IJPL-177087] methods
internal class FrontendXStackFrame(private val frameDto: XStackFrameDto) : XStackFrame() {
  val id: XStackFrameId get() = frameDto.stackFrameId

  override fun getSourcePosition(): XSourcePosition? {
    return frameDto.sourcePosition?.sourcePosition()
  }

  override fun getEqualityObject(): Any? = frameDto.equalityObject
}
