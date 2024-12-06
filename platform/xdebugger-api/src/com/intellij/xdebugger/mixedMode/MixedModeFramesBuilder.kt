// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XStackFrame

interface MixedModeFramesBuilder {
  /**
  * On exception only low level threads will be shown
   * frameToSelectIndex == null, means we agree to select the highest frame by default
   */
  data class MixedBuiltStackResult(val lowLevelToHighLevelFrameMap: Map<XStackFrame, XStackFrame?>, val frameToSelect: XStackFrame?)
  suspend fun buildMixedStack(
    session: XDebugSession,
    lowLevelFrames: List<XStackFrame>,
    highLevelFrames: List<XStackFrame>,
  ): MixedBuiltStackResult
}