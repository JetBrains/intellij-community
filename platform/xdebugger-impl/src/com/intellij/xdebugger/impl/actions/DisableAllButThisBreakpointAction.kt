package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DisableAllButThisBreakpointAction : AllButThisBreakpointAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun performAction(breakpointManager: XBreakpointManagerProxy, breakpoint: XBreakpointProxy) {
    breakpoint.setEnabled(false)
  }
}