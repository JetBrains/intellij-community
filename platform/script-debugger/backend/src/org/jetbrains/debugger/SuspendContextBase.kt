package org.jetbrains.debugger

import org.jetbrains.debugger.values.ValueManager

abstract class SuspendContextBase<VM: Vm, VALUE_MANAGER : ValueManager<VM>>(override val valueManager: VALUE_MANAGER, protected val explicitPaused: Boolean) : SuspendContext {
  override val state: SuspendState
    get() = if (exceptionData == null) (if (explicitPaused) SuspendState.PAUSED else SuspendState.NORMAL) else SuspendState.EXCEPTION

  override val script: Script?
    get() {
      val topFrame = topFrame
      return if (topFrame == null) null else valueManager.vm.scriptManager.getScript(topFrame)
    }
}