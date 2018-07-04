// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import com.intellij.xdebugger.breakpoints.XBreakpoint;

/**
 * @author nik
 */
public abstract class XBreakpointCustomPropertiesPanel<B extends XBreakpoint<?>> implements Disposable {

  @NotNull
  public abstract JComponent getComponent();

  public abstract void saveTo(@NotNull B breakpoint);

  public abstract void loadFrom(@NotNull B breakpoint);

  public void dispose() {
  }

  public boolean isVisibleOnPopup(@NotNull B breakpoint) {
    return true;
  }
}
