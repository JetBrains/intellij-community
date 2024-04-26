// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class TreeActions extends SwingActionDelegate {
  private TreeActions(String actionId) {
    super(actionId);
  }

  @Override
  protected @Nullable JTree getComponent(AnActionEvent event) {
    var component = super.getComponent(event);
    return component instanceof JTree tree && !speedSearchHandlesNavigation(component) ? tree : null;
  }

  public static final class Home extends TreeActions {
    public static final @NonNls String ID = "selectFirst";

    public Home() {
      super(ID);
    }
  }

  public static final class ShiftHome extends TreeActions {
    public static final @NonNls String ID = "selectFirstExtendSelection";

    public ShiftHome() {
      super(ID);
    }
  }

  public static final class End extends TreeActions {
    public static final @NonNls String ID = "selectLast";

    public End() {
      super(ID);
    }
  }

  public static final class ShiftEnd extends TreeActions {
    public static final @NonNls String ID = "selectLastExtendSelection";

    public ShiftEnd() {
      super(ID);
    }
  }

  public static final class Up extends TreeActions {
    public static final @NonNls String ID = "selectPrevious";

    public Up() {
      super(ID);
    }
  }

  public static final class ShiftUp extends TreeActions {
    public static final @NonNls String ID = "selectPreviousExtendSelection";

    public ShiftUp() {
      super(ID);
    }
  }

  public static final class Down extends TreeActions {
    public static final @NonNls String ID = "selectNext";

    public Down() {
      super(ID);
    }
  }

  public static final class ShiftDown extends TreeActions {
    public static final @NonNls String ID = "selectNextExtendSelection";

    public ShiftDown() {
      super(ID);
    }
  }

  public static final class SelectParent extends TreeActions {
    public static final @NonNls String ID = "selectParentNoCollapse";

    public SelectParent() {
      super(ID);
    }
  }

  public static final class Left extends TreeActions {
    public static final @NonNls String ID = "selectParent";

    public Left() {
      super(ID);
    }
  }

  public static final class ShiftLeft extends TreeActions {
    public static final @NonNls String ID = "selectParentExtendSelection";

    public ShiftLeft() {
      super(ID);
    }
  }

  public static final class Right extends TreeActions {
    public static final @NonNls String ID = "selectChild";

    public Right() {
      super(ID);
    }
  }

  public static final class ShiftRight extends TreeActions {
    public static final @NonNls String ID = "selectChildExtendSelection";

    public ShiftRight() {
      super(ID);
    }
  }

  public static final class PageUp extends TreeActions {
    public static final @NonNls String ID = "scrollUpChangeSelection";

    public PageUp() {
      super(ID);
    }
  }

  public static final class ShiftPageUp extends TreeActions {
    public static final @NonNls String ID = "scrollUpExtendSelection";

    public ShiftPageUp() {
      super(ID);
    }
  }

  public static final class PageDown extends TreeActions {
    public static final @NonNls String ID = "scrollDownChangeSelection";

    public PageDown() {
      super(ID);
    }
  }

  public static final class ShiftPageDown extends TreeActions {
    public static final @NonNls String ID = "scrollDownExtendSelection";

    public ShiftPageDown() {
      super(ID);
    }
  }

  public static final class NextSibling extends TreeActions {
    public static final @NonNls String ID = "selectNextSibling";

    public NextSibling() {
      super(ID);
    }
  }

  public static final class PreviousSibling extends TreeActions {
    public static final @NonNls String ID = "selectPreviousSibling";

    public PreviousSibling() {
      super(ID);
    }
  }
}
