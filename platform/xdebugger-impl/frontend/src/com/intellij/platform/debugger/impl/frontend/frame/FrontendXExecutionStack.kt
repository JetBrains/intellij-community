// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.icons.icon
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XExecutionStackDto
import com.intellij.xdebugger.impl.rpc.XExecutionStackId

internal class FrontendXExecutionStack(
  stackDto: XExecutionStackDto
) : XExecutionStack(stackDto.displayName, stackDto.icon?.icon()) {
  val id: XExecutionStackId = stackDto.executionStackId

  override fun getTopFrame(): XStackFrame? {
    // TODO[IJPL-177087]
    return null
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    // TODO[IJPL-177087]
  }
}
