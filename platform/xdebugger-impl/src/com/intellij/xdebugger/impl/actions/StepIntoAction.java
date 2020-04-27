// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class StepIntoAction extends XDebuggerActionBase {
  @Override
  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getStepIntoHandler();
  }
}
