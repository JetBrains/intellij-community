// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.frame.XStackFrame;

public abstract class XStackFrameEx extends XStackFrame {
  /**
   * Implement to run actions before navigation to this stack frame
   */
  public void beforeUpdateExecutionPosition() {
  }
}
