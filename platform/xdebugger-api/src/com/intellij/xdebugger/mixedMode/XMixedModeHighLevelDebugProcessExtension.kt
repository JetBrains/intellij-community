// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.annotations.ApiStatus

/**
 *  This extension is primarily used by MixedModeProcessTransitionStateMachine to coordinate high-level debugger for handling user actions
 *  (like, step or resume)
 *
 * Mixed mode debugging can be used when a process is debugged with two different debuggers.
 * For example, a Java application can be debugged using both a C++ debugger and a Java debugger.
 * Similarly, Python or .NET applications can be debugged using a C++ debugger,
 * and Blazor applications can be debugged using both a .NET debugger and a JavaScript debugger.
 * A high-level debug process is related to a debugger which provides a high-level view of a process (like Java, .NET, Python or Blazor debuggers)
 */
@ApiStatus.Internal
interface XMixedModeHighLevelDebugProcessExtension : XMixedModeDebugProcessExtension {
  fun pauseMixedModeSession()

  suspend fun isStepWillBringIntoLowLevelCode(suspendContext: XSuspendContext): Boolean

  /**
   * Check that the high-level debugger supports stopping in this suspend context
   */
  suspend fun canStopHere(lowSuspendContext: XSuspendContext): Boolean

  suspend fun refreshSuspendContextOnLowLevelStepFinish(suspendContext: XSuspendContext) : XSuspendContext?

  fun stoppedInHighLevelSuspendContext(suspendContext: XSuspendContext): Boolean

  suspend fun abortHighLevelStepping()

  fun setNextStatement(suspendContext: XSuspendContext, position: XSourcePosition)
}