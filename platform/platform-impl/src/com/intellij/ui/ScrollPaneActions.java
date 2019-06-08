// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public abstract class ScrollPaneActions extends SwingActionDelegate {
  private ScrollPaneActions(String actionId) {
    super(actionId);
  }

  @Override
  protected JComponent getComponent(AnActionEvent event) {
    return ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, (Component)super.getComponent(event));
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
