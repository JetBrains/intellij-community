/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.frame;

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

  public XExecutionStack[] getExecutionStacks() {
    XExecutionStack executionStack = getActiveExecutionStack();
    return executionStack != null ? new XExecutionStack[]{executionStack} : XExecutionStack.EMPTY_ARRAY;
  }

  public void computeExecutionStacks(XExecutionStackContainer container) {
    container.addExecutionStack(Arrays.asList(getExecutionStacks()), true);
  }

  public interface XExecutionStackContainer extends XValueCallback {
    void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, final boolean last);
  }
}
