// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XSuspendContext

interface XMixedModeDebugProcess {
  suspend fun resumeAndWait(): Boolean
  fun getStoppedThreadId(context : XSuspendContext) : Long
}

val XMixedModeDebugProcess.asXDebugProcess: XDebugProcess
  get() = this as XDebugProcess