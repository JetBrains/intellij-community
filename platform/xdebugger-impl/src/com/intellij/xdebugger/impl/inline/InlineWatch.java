// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.InlineWatchState;

import java.util.Objects;

public class InlineWatch {
  private final XExpression myExpression;
  private final XSourcePosition myPosition;

  public InlineWatch(XExpression expression, XSourcePosition position) {
    myExpression = expression;
    myPosition = position;
  }

  public InlineWatch(InlineWatchState st) {
    myExpression = Objects.requireNonNull(st.getWatchState()).toXExpression();
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(st.getFileUrl());

    myPosition = XDebuggerUtil.getInstance().createPosition(file, st.getLine());
  }

  public XExpression getExpression() {
    return myExpression;
  }

  public XSourcePosition getPosition() {
    return myPosition;
  }
}
