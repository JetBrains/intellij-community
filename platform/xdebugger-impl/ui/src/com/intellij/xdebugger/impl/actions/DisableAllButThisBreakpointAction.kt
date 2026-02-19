package com.intellij.xdebugger.impl.actions

import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DisableAllButThisBreakpointAction : AllButThisBreakpointAction(), SplitDebuggerAction {
  override fun performAction(breakpointManager: XBreakpointManagerProxy, breakpoint: XBreakpointProxy) {
    breakpoint.setEnabled(false)
  }
}