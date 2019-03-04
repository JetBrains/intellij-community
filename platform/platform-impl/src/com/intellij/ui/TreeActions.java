// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;

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
    @NonNls public static final String ID = "selectFirst";

    public Home() {
      super(ID);
    }
  }

  public static final class ShiftHome extends TreeActions {
    @NonNls public static final String ID = "selectFirstExtendSelection";

    public ShiftHome() {
      super(ID);
    }
  }

  public static final class End extends TreeActions {
    @NonNls public static final String ID = "selectLast";

    public End() {
      super(ID);
    }
  }

  public static final class ShiftEnd extends TreeActions {
    @NonNls public static final String ID = "selectLastExtendSelection";

    public ShiftEnd() {
      super(ID);
    }
  }

  public static final class Up extends TreeActions {
    @NonNls public static final String ID = "selectPrevious";

    public Up() {
      super(ID);
    }
  }

  public static final class ShiftUp extends TreeActions {
    @NonNls public static final String ID = "selectPreviousExtendSelection";

    public ShiftUp() {
      super(ID);
    }
  }

  public static final class Down extends TreeActions {
    @NonNls public static final String ID = "selectNext";

    public Down() {
      super(ID);
    }
  }

  public static final class ShiftDown extends TreeActions {
    @NonNls public static final String ID = "selectNextExtendSelection";

    public ShiftDown() {
      super(ID);
    }
  }

  public static final class Left extends TreeActions {
    @NonNls public static final String ID = "selectParent";

    public Left() {
      super(ID);
    }
  }

  public static final class ShiftLeft extends TreeActions {
    @NonNls public static final String ID = "selectParentExtendSelection";

    public ShiftLeft() {
      super(ID);
    }
  }

  public static final class Right extends TreeActions {
    @NonNls public static final String ID = "selectChild";

    public Right() {
      super(ID);
    }
  }

  public static final class ShiftRight extends TreeActions {
    @NonNls public static final String ID = "selectChildExtendSelection";

    public ShiftRight() {
      super(ID);
    }
  }

  public static final class PageUp extends TreeActions {
    @NonNls public static final String ID = "scrollUpChangeSelection";

    public PageUp() {
      super(ID);
    }
  }

  public static final class ShiftPageUp extends TreeActions {
    @NonNls public static final String ID = "scrollUpExtendSelection";

    public ShiftPageUp() {
      super(ID);
    }
  }

  public static final class PageDown extends TreeActions {
    @NonNls public static final String ID = "scrollDownChangeSelection";

    public PageDown() {
      super(ID);
    }
  }

  public static final class ShiftPageDown extends TreeActions {
    @NonNls public static final String ID = "scrollDownExtendSelection";

    public ShiftPageDown() {
      super(ID);
    }
  }
}
