// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.xdebugger.XDebugSession

interface MixedModeFramesBuilder {
  /**
  * On exception only low level threads will be shown
   */
  suspend fun buildMixedStack(
    session: XDebugSession,
    lowLevelFrames: List<XStackFrame>,
    highLevelFrames: List<XStackFrame>,
  ): List<XStackFrame>
}