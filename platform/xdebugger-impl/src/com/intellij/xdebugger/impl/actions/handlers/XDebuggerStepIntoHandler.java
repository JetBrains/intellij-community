// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.util.List;

@ApiStatus.Internal
public class XDebuggerStepIntoHandler extends XDebuggerSmartStepIntoHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    return XDebuggerSuspendedActionHandler.isEnabled(session);
  }

  @Override
  protected <V extends XSmartStepIntoVariant> Promise<List<V>> computeVariants(XSmartStepIntoHandler<V> handler, XSourcePosition position) {
    return handler.computeStepIntoVariants(position);
  }

  @Override
  protected <V extends XSmartStepIntoVariant> boolean handleSimpleCases(XSmartStepIntoHandler<V> handler,
                                                                        List<? extends V> variants,
                                                                        XDebugSession session) {
    if (variants.size() == 1) {
      V singleVariant = variants.get(0);
      if (singleVariant instanceof ForceSmartStepIntoSource forceSmartStepIntoSource && forceSmartStepIntoSource.needForceSmartStepInto()) {
        return super.handleSimpleCases(handler, variants, session);
      }
    }

    if (variants.size() < 2) {
      session.stepInto();
      return true;
    }
    return false;
  }
}
