// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.JList;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Sergey.Malenkov
 */
public abstract class ListActions extends SwingActionDelegate {
  private ListActions(String actionId) {
    super(actionId);
  }

  @Override
  protected JList getComponent(AnActionEvent event) {
    return tryCast(super.getComponent(event), JList.class);
  }

  public static final class Home extends ListActions {
    public Home() {
      super("selectFirstRow");
    }
  }

  public static final class ShiftHome extends ListActions {
    public ShiftHome() {
      super("selectFirstRowExtendSelection");
    }
  }

  public static final class End extends ListActions {
    public End() {
      super("selectLastRow");
    }
  }

  public static final class ShiftEnd extends ListActions {
    public ShiftEnd() {
      super("selectLastRowExtendSelection");
    }
  }

  public static final class Up extends ListActions {
    public Up() {
      super("selectPreviousRow");
    }
  }

  public static final class ShiftUp extends ListActions {
    public ShiftUp() {
      super("selectPreviousRowExtendSelection");
    }
  }

  public static final class Down extends ListActions {
    public Down() {
      super("selectNextRow");
    }
  }

  public static final class ShiftDown extends ListActions {
    public ShiftDown() {
      super("selectNextRowExtendSelection");
    }
  }

  public static final class Left extends ListActions {
    public Left() {
      super("selectPreviousColumn");
    }
  }

  public static final class ShiftLeft extends ListActions {
    public ShiftLeft() {
      super("selectPreviousColumnExtendSelection");
    }
  }

  public static final class Right extends ListActions {
    public Right() {
      super("selectNextColumn");
    }
  }

  public static final class ShiftRight extends ListActions {
    public ShiftRight() {
      super("selectNextColumnExtendSelection");
    }
  }

  public static final class PageUp extends ListActions {
    public PageUp() {
      super("scrollUp");
    }
  }

  public static final class ShiftPageUp extends ListActions {
    public ShiftPageUp() {
      super("scrollUpExtendSelection");
    }
  }

  public static final class PageDown extends ListActions {
    public PageDown() {
      super("scrollDown");
    }
  }

  public static final class ShiftPageDown extends ListActions {
    public ShiftPageDown() {
      super("scrollDownExtendSelection");
    }
  }
}
