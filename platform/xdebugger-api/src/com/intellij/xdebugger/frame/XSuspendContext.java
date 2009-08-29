package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XSuspendContext {

  @Nullable
  public XExecutionStack getActiveExecutionStack() {
    return null;
  }

  public XExecutionStack[] getExecutionStacks() {
    XExecutionStack executionStack = getActiveExecutionStack();
    return executionStack != null ? new XExecutionStack[]{executionStack} : XExecutionStack.EMPTY_ARRAY;
  }

}
