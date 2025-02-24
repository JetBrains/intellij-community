// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.annotations.ApiStatus

/**
 * Used when building a mixed call stack
 * Implementations of this class need to be able to match low-level debug frames with high-level ones
 */
@ApiStatus.Internal
interface MixedModeStackBuilder {
  /**
  * If an unhandled exception occurs within this method, only low-level threads will be shown
   * frameToSelectIndex == null, means we agree to select the highest frame by default
   */
  @ApiStatus.Internal
  data class MixedBuiltStackResult(val lowLevelToHighLevelFrameMap: Map<XStackFrame, XStackFrame?>, val highestHighLevelFrame: XStackFrame?)
  suspend fun buildMixedStack(
    lowExecutionStack: XExecutionStack,
    lowLevelFrames: List<XStackFrame>,
    highLevelFrames: List<XStackFrame>,
  ): MixedBuiltStackResult

  /**
   * Sets filtering for built mixed stack.
   * It's called when the user has isShowLibraryStackFrames turned on
   */
  fun filterFrames(frames: List<XStackFrame>): List<XStackFrame>
}