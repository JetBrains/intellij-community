package com.intellij.xdebugger.impl.actions

import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointManager

class DisableAllButThisBreakpointAction : AllButThisBreakpointAction() {
    override fun performAction(breakpointManager: XBreakpointManager, breakpoint: XBreakpoint<*>) {
        breakpoint.isEnabled = false
    }
}