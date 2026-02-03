// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public abstract class ScrollPaneActions extends SwingActionDelegate {
  private ScrollPaneActions(@NonNls String actionId) {
    super(actionId);
  }

  @Override
  protected @Nullable JComponent getComponent(AnActionEvent event) {
    return ComponentUtil.getParentOfType(JScrollPane.class, super.getComponent(event));
  }

  public static final class Home extends ScrollPaneActions {
    public Home() {
      super("scrollHome");
    }
  }

  public static final class End extends ScrollPaneActions {
    public End() {
      super("scrollEnd");
    }
  }

  public static final class Up extends ScrollPaneActions {
    public Up() {
      super("unitScrollUp");
    }
  }

  public static final class Down extends ScrollPaneActions {
    public Down() {
      super("unitScrollDown");
    }
  }

  public static final class Left extends ScrollPaneActions {
    public Left() {
      super("unitScrollLeft");
    }
  }

  public static final class Right extends ScrollPaneActions {
    public Right() {
      super("unitScrollRight");
    }
  }

  public static final class PageUp extends ScrollPaneActions {
    public PageUp() {
      super("scrollUp");
    }
  }

  public static final class PageDown extends ScrollPaneActions {
    public PageDown() {
      super("scrollDown");
    }
  }

  public static final class PageLeft extends ScrollPaneActions {
    public PageLeft() {
      super("scrollLeft");
    }
  }

  public static final class PageRight extends ScrollPaneActions {
    public PageRight() {
      super("scrollRight");
    }
  }
}
