// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class TableActions extends SwingActionDelegate {
  private TableActions(String actionId) {
    super(actionId);
  }

  @Override
  protected @Nullable JTable getComponent(AnActionEvent event) {
    var component = super.getComponent(event);
    return component instanceof JTable table && SpeedSearchSupply.getSupply(component) == null ? table : null;
  }

  public static final class CtrlHome extends TableActions {
    @NonNls public static final String ID = "selectFirstRow";

    public CtrlHome() {
      super(ID);
    }
  }

  public static final class CtrlShiftHome extends TableActions {
    @NonNls public static final String ID = "selectFirstRowExtendSelection";

    public CtrlShiftHome() {
      super(ID);
    }
  }

  public static final class CtrlEnd extends TableActions {
    @NonNls public static final String ID = "selectLastRow";

    public CtrlEnd() {
      super(ID);
    }
  }

  public static final class CtrlShiftEnd extends TableActions {
    @NonNls public static final String ID = "selectLastRowExtendSelection";

    public CtrlShiftEnd() {
      super(ID);
    }
  }

  public static final class Up extends TableActions {
    @NonNls public static final String ID = "selectPreviousRow";

    public Up() {
      super(ID);
    }
  }

  public static final class ShiftUp extends TableActions {
    @NonNls public static final String ID = "selectPreviousRowExtendSelection";

    public ShiftUp() {
      super(ID);
    }
  }

  public static final class Down extends TableActions {
    @NonNls public static final String ID = "selectNextRow";

    public Down() {
      super(ID);
    }
  }

  public static final class ShiftDown extends TableActions {
    @NonNls public static final String ID = "selectNextRowExtendSelection";

    public ShiftDown() {
      super(ID);
    }
  }

  public static final class Left extends TableActions {
    @NonNls public static final String ID = "selectPreviousColumn";

    public Left() {
      super(ID);
    }
  }

  public static final class ShiftLeft extends TableActions {
    @NonNls public static final String ID = "selectPreviousColumnExtendSelection";

    public ShiftLeft() {
      super(ID);
    }
  }

  public static final class Right extends TableActions {
    @NonNls public static final String ID = "selectNextColumn";

    public Right() {
      super(ID);
    }
  }

  public static final class ShiftRight extends TableActions {
    @NonNls public static final String ID = "selectNextColumnExtendSelection";

    public ShiftRight() {
      super(ID);
    }
  }

  public static final class PageUp extends TableActions {
    @NonNls public static final String ID = "scrollUpChangeSelection";

    public PageUp() {
      super(ID);
    }
  }

  public static final class ShiftPageUp extends TableActions {
    @NonNls public static final String ID = "scrollUpExtendSelection";

    public ShiftPageUp() {
      super(ID);
    }
  }

  public static final class PageDown extends TableActions {
    @NonNls public static final String ID = "scrollDownChangeSelection";

    public PageDown() {
      super(ID);
    }
  }

  public static final class ShiftPageDown extends TableActions {
    @NonNls public static final String ID = "scrollDownExtendSelection";

    public ShiftPageDown() {
      super(ID);
    }
  }
}
