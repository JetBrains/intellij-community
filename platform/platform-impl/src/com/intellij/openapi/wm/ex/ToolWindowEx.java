// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.InternalDecorator;
import org.jetbrains.annotations.Nullable;

public interface ToolWindowEx extends ToolWindow {
  /**
   * @return type of internal decoration of tool window.
   * @throws IllegalStateException
   *          if tool window isn't installed.
   */
  ToolWindowType getInternalType();

  void stretchWidth(int value);

  void stretchHeight(int value);

  InternalDecorator getDecorator();

  void setAdditionalGearActions(@Nullable ActionGroup additionalGearActions);

  void setTitleActions(AnAction... actions);

  void setTabActions(AnAction... actions);

  /**
   * @deprecated Not used.
   */
  @Deprecated
  default void setUseLastFocusedOnActivation(@SuppressWarnings("unused") boolean focus) {
  }
}
