// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.annotations.ApiStatus


/**
 * Marker interface for a stack frame that contains hidden frames in a debugging session.
 */
@ApiStatus.Internal
interface HiddenFramesStackFrame {
  fun getHiddenFrames(): List<XStackFrame>
}
