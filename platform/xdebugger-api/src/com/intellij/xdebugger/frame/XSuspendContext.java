// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a suspended state of a debug process
 *
 * @author nik
 */
public abstract class XSuspendContext {

  /**
   * Returned execution stack will be selected by default in 'Frames' panel of 'Debug' tool window. Also it will be used to obtain current
   * stack frame to perform 'Evaluate' action, for example
   */
  @Nullable
  public XExecutionStack getActiveExecutionStack() {
    return null;
  }

  @NotNull
  public XExecutionStack[] getExecutionStacks() {
    XExecutionStack executionStack = getActiveExecutionStack();
    return executionStack != null ? new XExecutionStack[]{executionStack} : XExecutionStack.EMPTY_ARRAY;
  }

  public void computeExecutionStacks(XExecutionStackContainer container) {
    container.addExecutionStack(Arrays.asList(getExecutionStacks()), true);
  }

  public interface XExecutionStackContainer extends XValueCallback, Obsolescent {
    void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, final boolean last);
  }
}
