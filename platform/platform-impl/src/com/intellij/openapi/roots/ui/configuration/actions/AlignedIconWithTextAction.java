// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class AlignedIconWithTextAction extends IconWithTextAction {
  private static final int SIDE_BORDER_WIDTH = 4;

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponentImpl(this, presentation, place);
  }

  @NotNull
  public static JComponent createCustomComponentImpl(@NotNull AnAction action, @NotNull Presentation presentation, @NotNull String place) {
    return align(IconWithTextAction.createCustomComponentImpl(action, presentation, place));
  }

  @NotNull
  public static JComponent align(@NotNull JComponent c) {
    Insets i = new JCheckBox().getInsets();
    c.setBorder(JBUI.Borders.empty(i.top, SIDE_BORDER_WIDTH, i.bottom, SIDE_BORDER_WIDTH));
    return c;
  }

  public abstract static class Group extends ActionGroup implements CustomComponentAction {

    public Group() {
      setPopup(true);
      getTemplatePresentation().setPerformGroup(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) { return EMPTY_ARRAY; }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return createCustomComponentImpl(this, presentation, place);
    }
  }
}
