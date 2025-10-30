package com.intellij.xdebugger.impl.actions

import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DisableAllButThisBreakpointAction : AllButThisBreakpointAction(), SplitDebuggerAction {
  override fun performAction(breakpointManager: XBreakpointManagerProxy, breakpoint: XBreakpointProxy) {
    breakpoint.setEnabled(false)
  }
}