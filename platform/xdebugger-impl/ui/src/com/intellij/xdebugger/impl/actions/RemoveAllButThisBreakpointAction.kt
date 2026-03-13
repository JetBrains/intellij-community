package com.intellij.xdebugger.impl.actions

import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RemoveAllButThisBreakpointAction : AllButThisBreakpointAction(), SplitDebuggerAction {
  override fun performAction(breakpointManager: XBreakpointManagerProxy, breakpoint: XBreakpointProxy) {
    breakpointManager.removeBreakpoint(breakpoint)
  }
}