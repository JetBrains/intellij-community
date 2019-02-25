// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.JTree;

import static com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Sergey.Malenkov
 */
public abstract class TreeActions extends SwingActionDelegate {
  private TreeActions(String actionId) {
    super(actionId);
  }

  @Override
  protected JTree getComponent(AnActionEvent event) {
    JTree tree = tryCast(super.getComponent(event), JTree.class);
    return tree == null || getSupply(tree) != null ? null : tree;
  }

  public static final class Home extends TreeActions {
    public Home() {
      super("selectFirst");
    }
  }

  public static final class ShiftHome extends TreeActions {
    public ShiftHome() {
      super("selectFirstExtendSelection");
    }
  }

  public static final class End extends TreeActions {
    public End() {
      super("selectLast");
    }
  }

  public static final class ShiftEnd extends TreeActions {
    public ShiftEnd() {
      super("selectLastExtendSelection");
    }
  }

  public static final class Up extends TreeActions {
    public Up() {
      super("selectPrevious");
    }
  }

  public static final class ShiftUp extends TreeActions {
    public ShiftUp() {
      super("selectPreviousExtendSelection");
    }
  }

  public static final class Down extends TreeActions {
    public Down() {
      super("selectNext");
    }
  }

  public static final class ShiftDown extends TreeActions {
    public ShiftDown() {
      super("selectNextExtendSelection");
    }
  }

  public static final class Left extends TreeActions {
    public Left() {
      super("selectParent");
    }
  }

  public static final class ShiftLeft extends TreeActions {
    public ShiftLeft() {
      super("selectParentExtendSelection");
    }
  }

  public static final class Right extends TreeActions {
    public Right() {
      super("selectChild");
    }
  }

  public static final class ShiftRight extends TreeActions {
    public ShiftRight() {
      super("selectChildExtendSelection");
    }
  }

  public static final class PageUp extends TreeActions {
    public PageUp() {
      super("scrollUpChangeSelection");
    }
  }

  public static final class ShiftPageUp extends TreeActions {
    public ShiftPageUp() {
      super("scrollUpExtendSelection");
    }
  }

  public static final class PageDown extends TreeActions {
    public PageDown() {
      super("scrollDownChangeSelection");
    }
  }

  public static final class ShiftPageDown extends TreeActions {
    public ShiftPageDown() {
      super("scrollDownExtendSelection");
    }
  }
}
