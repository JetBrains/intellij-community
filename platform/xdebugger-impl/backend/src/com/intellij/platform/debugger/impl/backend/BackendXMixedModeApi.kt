// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XMixedModeApi
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.mixedmode.highLevelProcessOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelMixedModeExtensionOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelProcessOrThrow
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.ui.useSplitterView

internal class BackendXMixedModeApi : XMixedModeApi {

  override suspend fun isMixedModeSession(sessionId: XDebugSessionId): Boolean = sessionId.findValue()!!.isMixedMode

  override suspend fun showCustomizedEvaluatorView(frameId: XStackFrameId): Boolean {
    val session = frameId.findValue()!!.session
    assert(isMixedModeSession(session.id))

    val frame = frameId.findValue()!!.stackFrame
    val useLowLevelPanel = session.lowLevelMixedModeExtensionOrThrow.belongsToMe(frame)
    val useHighLevelPanel = !useLowLevelPanel
    val lowSupportsCustomization = session.lowLevelProcessOrThrow.useSplitterView()
    val highSupportsCustomization = session.highLevelProcessOrThrow.useSplitterView()
    return useLowLevelPanel && lowSupportsCustomization || useHighLevelPanel && highSupportsCustomization
  }
}
