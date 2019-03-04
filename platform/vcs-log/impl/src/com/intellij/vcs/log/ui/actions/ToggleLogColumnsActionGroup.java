// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ToggleLogColumnsActionGroup extends ActionGroup implements DumbAware {
  public ToggleLogColumnsActionGroup() {
    super("Show Columns", true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
    e.getPresentation().setIcon(e.isFromActionToolbar() ? AllIcons.Actions.Show : null);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = new ArrayList<>();
    for (int column : GraphTableModel.DYNAMIC_COLUMNS) {
      actions.add(new ToggleColumnAction(column));
    }

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  private static boolean isEnabledAndVisible(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    return properties != null && properties.exists(CommonUiProperties.COLUMN_ORDER);
  }

  private static class ToggleColumnAction extends ToggleAction implements DumbAware {
    private final int myIndex;

    private ToggleColumnAction(int index) {
      super(GraphTableModel.COLUMN_NAMES[index]);
      myIndex = index;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      if (properties != null && properties.exists(CommonUiProperties.COLUMN_ORDER)) {
        List<Integer> columnOrder = properties.get(CommonUiProperties.COLUMN_ORDER);
        return columnOrder.contains(myIndex);
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VcsLogUiProperties properties = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      assert properties.exists(CommonUiProperties.COLUMN_ORDER);

      List<Integer> columnOrder = new ArrayList<>(properties.get(CommonUiProperties.COLUMN_ORDER));
      if (columnOrder.contains(myIndex)) {
        columnOrder.remove((Integer)myIndex);
      }
      else {
        columnOrder.add(myIndex);
      }
      properties.set(CommonUiProperties.COLUMN_ORDER, columnOrder);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
    }
  }
}
