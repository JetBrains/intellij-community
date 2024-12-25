// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.Deferred

interface XMixedModeLowLevelDebugProcess : XMixedModeDebugProcess {
  val ready : Deferred<Unit>

  suspend fun continueAllThreads(exceptEventThread: Boolean, silent : Boolean)
  suspend fun continueHighDebuggerServiceThreads()

  /**
   * High-level debugger may hold the threads on a kernel sync primitive when the debugger session is stopped
   * If a thread is blocked in the kernel, LLDB can't evaluate.
   * In this function we try to unblock a thread to leave the kernel code and stop in the callee
   *
   * NOTE: If the thread is not stopped by managed debugger in the described way, we'll do nothing, the thread will stay blocked in the kernel code
   **/
  suspend fun pauseMixedModeSessionUnBlockStopEventThread(stopEventThreadId: Long)

  fun pauseMixedModeSession(stopEventThreadId: Long)
  suspend fun startMixedStepInto(steppingThreadId: Long, ctx: XSuspendContext): Int
  suspend fun removeTempBreakpoint(brId: Int)
  suspend fun prepareThreadBeforeFramesComputation(threadId: Long)
}