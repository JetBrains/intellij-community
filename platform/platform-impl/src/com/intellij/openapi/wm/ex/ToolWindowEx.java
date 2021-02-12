// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.InternalDecorator;
import org.jetbrains.annotations.ApiStatus;
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

  @NotNull Project getProject();

  void stretchWidth(int value);

  void stretchHeight(int value);

  @NotNull InternalDecorator getDecorator();

  void setAdditionalGearActions(@Nullable ActionGroup additionalGearActions);

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

  /**
   * @deprecated Not used.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  default void setUseLastFocusedOnActivation(@SuppressWarnings("unused") boolean focus) {
  }

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
    private Icon myIcon;

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

}
