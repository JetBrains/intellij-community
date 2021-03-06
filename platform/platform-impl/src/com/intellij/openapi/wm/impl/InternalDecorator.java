// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.ActionToolbar;

import javax.swing.*;

public abstract class InternalDecorator extends JPanel {
  /**
   * See {@link com.intellij.ide.actions.ToolWindowViewModeAction} and {@link com.intellij.ide.actions.ToolWindowMoveAction}
   */
  public static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  public static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  public static final String TOGGLE_SIDE_MODE_ACTION_ID = "ToggleSideMode";

  public abstract ActionToolbar getHeaderToolbar();

  public abstract int getHeaderHeight();

  public abstract void setHeaderVisible(boolean value);

  public abstract boolean isHeaderVisible();
}
