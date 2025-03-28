// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerSuspendedActionHandler extends XDebuggerActionHandler {
  @ApiStatus.Internal
  @Override
  protected boolean isEnabled(@NotNull XDebugSessionProxy session, @NotNull DataContext dataContext) {
    return isEnabled(session);
  }

  @ApiStatus.Internal
  public static boolean isEnabled(@NotNull XDebugSessionProxy session) {
    return !session.isReadOnly() && session.isSuspended();
  }

  /**
   * @deprecated Use {@link XDebuggerSuspendedActionHandler#isEnabled(XDebugSessionProxy)} instead
   */
  @Deprecated
  public static boolean isEnabled(@NotNull XDebugSession session) {
    return !((XDebugSessionImpl)session).isReadOnly() && session.isSuspended();
  }
}
