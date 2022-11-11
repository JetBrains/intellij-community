// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Spacer could be combined with ActionToolbar component in order to prevent
 * collapsing of toolbar containing panel when toolbar action group is empty.
 */
public class ActionToolbarSpacer extends JLabel {
  private static final AnAction EMPTY_ACTION = new DumbAwareAction(EmptyIcon.ICON_16) {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
    }
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  };

  private static ActionToolbar createPrototypeToolbar(boolean horizontal) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(EMPTY_ACTION);
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, horizontal);
  }

  private final boolean myHorizontal;

  public ActionToolbarSpacer(boolean horizontal) {
    myHorizontal = horizontal;
    ActionToolbar prototypeToolbar = createPrototypeToolbar(horizontal);
    if (horizontal) {
      setPreferredSize(new Dimension(0, prototypeToolbar.getComponent().getPreferredSize().height));
    }
    else {
      setPreferredSize(new Dimension(prototypeToolbar.getComponent().getPreferredSize().width, 0));
    }
  }

  public boolean isHorizontal() {
    return myHorizontal;
  }
}
