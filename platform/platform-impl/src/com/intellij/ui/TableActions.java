// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.JTable;

import static com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Sergey.Malenkov
 */
public abstract class TableActions extends SwingActionDelegate {
  private TableActions(String actionId) {
    super(actionId);
  }

  @Override
  protected JTable getComponent(AnActionEvent event) {
    JTable table = tryCast(super.getComponent(event), JTable.class);
    return table == null || getSupply(table) != null ? null : table;
  }

  public static final class CtrlHome extends TableActions {
    public CtrlHome() {
      super("selectFirstRow");
    }
  }

  public static final class CtrlShiftHome extends TableActions {
    public CtrlShiftHome() {
      super("selectFirstRowExtendSelection");
    }
  }

  public static final class CtrlEnd extends TableActions {
    public CtrlEnd() {
      super("selectLastRow");
    }
  }

  public static final class CtrlShiftEnd extends TableActions {
    public CtrlShiftEnd() {
      super("selectLastRowExtendSelection");
    }
  }

  public static final class Up extends TableActions {
    public Up() {
      super("selectPreviousRow");
    }
  }

  public static final class ShiftUp extends TableActions {
    public ShiftUp() {
      super("selectPreviousRowExtendSelection");
    }
  }

  public static final class Down extends TableActions {
    public Down() {
      super("selectNextRow");
    }
  }

  public static final class ShiftDown extends TableActions {
    public ShiftDown() {
      super("selectNextRowExtendSelection");
    }
  }

  public static final class Left extends TableActions {
    public Left() {
      super("selectPreviousColumn");
    }
  }

  public static final class ShiftLeft extends TableActions {
    public ShiftLeft() {
      super("selectPreviousColumnExtendSelection");
    }
  }

  public static final class Right extends TableActions {
    public Right() {
      super("selectNextColumn");
    }
  }

  public static final class ShiftRight extends TableActions {
    public ShiftRight() {
      super("selectNextColumnExtendSelection");
    }
  }

  public static final class PageUp extends TableActions {
    public PageUp() {
      super("scrollUpChangeSelection");
    }
  }

  public static final class ShiftPageUp extends TableActions {
    public ShiftPageUp() {
      super("scrollUpExtendSelection");
    }
  }

  public static final class PageDown extends TableActions {
    public PageDown() {
      super("scrollDownChangeSelection");
    }
  }

  public static final class ShiftPageDown extends TableActions {
    public ShiftPageDown() {
      super("scrollDownExtendSelection");
    }
  }
}
