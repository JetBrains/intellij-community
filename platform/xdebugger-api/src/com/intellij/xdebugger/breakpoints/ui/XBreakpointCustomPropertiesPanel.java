// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.openapi.Disposable;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public abstract class XBreakpointCustomPropertiesPanel<B extends XBreakpoint<?>> implements Disposable {

  public abstract @NotNull JComponent getComponent();

  public abstract void saveTo(@NotNull B breakpoint);

  public abstract void loadFrom(@NotNull B breakpoint);

  @Override
  public void dispose() {
  }

  public boolean isVisibleOnPopup(@NotNull B breakpoint) {
    return true;
  }

  /**
   * Whether this panel already identifies the breakpoint and the generic breakpoint name/description label should be
   * hidden while it is shown. Opt-in: returns {@code false} by default, so existing panels keep the standard name label.
   */
  @ApiStatus.Internal
  public boolean hidesBreakpointNameLabel() {
    return false;
  }
}
