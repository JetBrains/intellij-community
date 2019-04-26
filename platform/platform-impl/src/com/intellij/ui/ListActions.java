// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;

import javax.swing.JList;

import static com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply;
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
    JList list = tryCast(super.getComponent(event), JList.class);
    return list == null || getSupply(list) != null ? null : list;
  }

  public static final class Home extends ListActions {
    @NonNls public static final String ID = "selectFirstRow";

    public Home() {
      super(ID);
    }
  }

  public static final class ShiftHome extends ListActions {
    @NonNls public static final String ID = "selectFirstRowExtendSelection";

    public ShiftHome() {
      super(ID);
    }
  }

  public static final class End extends ListActions {
    @NonNls public static final String ID = "selectLastRow";

    public End() {
      super(ID);
    }
  }

  public static final class ShiftEnd extends ListActions {
    @NonNls public static final String ID = "selectLastRowExtendSelection";

    public ShiftEnd() {
      super(ID);
    }
  }

  public static final class Up extends ListActions {
    @NonNls public static final String ID = "selectPreviousRow";

    public Up() {
      super(ID);
    }
  }

  public static final class ShiftUp extends ListActions {
    @NonNls public static final String ID = "selectPreviousRowExtendSelection";

    public ShiftUp() {
      super(ID);
    }
  }

  public static final class Down extends ListActions {
    @NonNls public static final String ID = "selectNextRow";

    public Down() {
      super(ID);
    }
  }

  public static final class ShiftDown extends ListActions {
    @NonNls public static final String ID = "selectNextRowExtendSelection";

    public ShiftDown() {
      super(ID);
    }
  }

  public static final class Left extends ListActions {
    @NonNls public static final String ID = "selectPreviousColumn";

    public Left() {
      super(ID);
    }
  }

  public static final class ShiftLeft extends ListActions {
    @NonNls public static final String ID = "selectPreviousColumnExtendSelection";

    public ShiftLeft() {
      super(ID);
    }
  }

  public static final class Right extends ListActions {
    @NonNls public static final String ID = "selectNextColumn";

    public Right() {
      super(ID);
    }
  }

  public static final class ShiftRight extends ListActions {
    @NonNls public static final String ID = "selectNextColumnExtendSelection";

    public ShiftRight() {
      super(ID);
    }
  }

  public static final class PageUp extends ListActions {
    @NonNls public static final String ID = "scrollUp";

    public PageUp() {
      super(ID);
    }
  }

  public static final class ShiftPageUp extends ListActions {
    @NonNls public static final String ID = "scrollUpExtendSelection";

    public ShiftPageUp() {
      super(ID);
    }
  }

  public static final class PageDown extends ListActions {
    @NonNls public static final String ID = "scrollDown";

    public PageDown() {
      super(ID);
    }
  }

  public static final class ShiftPageDown extends ListActions {
    @NonNls public static final String ID = "scrollDownExtendSelection";

    public ShiftPageDown() {
      super(ID);
    }
  }
}
