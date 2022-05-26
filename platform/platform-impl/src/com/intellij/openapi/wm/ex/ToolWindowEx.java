// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.Arrays;
import java.util.List;

public interface ToolWindowEx extends ToolWindow {
  /**
   * @return type of internal decoration of tool window.
   * @throws IllegalStateException if tool window isn't installed.
   */
  @NotNull ToolWindowType getInternalType();

  void stretchWidth(int value);

  void stretchHeight(int value);

  default boolean canCloseContents() {
    return false;
  }

  @NotNull InternalDecorator getDecorator();

  /**
   * @deprecated Use {@link #setTitleActions(List)}
   */
  @Deprecated
  default void setTitleActions(@NotNull AnAction @NotNull ... actions) {
    setTitleActions(Arrays.asList(actions));
  }

  void setTabActions(@NotNull AnAction @NotNull ... actions);

  void setTabDoubleClickActions(@NotNull List<AnAction> actions);

  @Nullable
  default ToolWindowDecoration getDecoration() { return null; }

  final class Border extends EmptyBorder {
    public Border() {
      this(true, true, true, true);
    }

    public Border(boolean top, boolean left, boolean right, boolean bottom) {
      super(top ? 2 : 0, left ? 2 : 0, right ? 2 : 0, bottom ? 2 : 0);
    }
  }

  final class ToolWindowDecoration {
    private final ActionGroup myActionGroup;
    private final Icon myIcon;

    public ToolWindowDecoration(Icon icon, ActionGroup actionGroup) {
      myActionGroup = actionGroup;
      myIcon = icon;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public ActionGroup getActionGroup() {
      return myActionGroup;
    }
  }

  @Nullable
  default StatusText getEmptyText() {
    return null;
  }
}
