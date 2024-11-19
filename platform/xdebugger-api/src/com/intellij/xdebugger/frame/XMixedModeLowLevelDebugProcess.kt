// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

interface XMixedModeLowLevelDebugProcess : XMixedModeDebugProcess{
  suspend fun continueAllThreads(exceptEventThread: Boolean)
  suspend fun pauseMixedModeSessionUnBlockStopEventThread(stopEventThreadId: Long, triggerBlockedByManagedDebuggerThreadSpin: (suspend () -> Unit))
  fun pauseMixedModeSession(stopEventThreadId : Long)

  suspend fun findAndSetBreakpointInNativeFunction(steppingThreadId: Long, trigger: suspend (() -> Unit)): Int
  suspend fun removeTempBreakpoint(brId: Int)
}