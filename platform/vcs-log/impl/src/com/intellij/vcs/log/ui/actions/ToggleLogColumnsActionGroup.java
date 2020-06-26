// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ToggleLogColumnsActionGroup extends ActionGroup implements DumbAware {

  public ToggleLogColumnsActionGroup() {
    super(VcsLogBundle.message("action.title.select.columns.to.see"),
          VcsLogBundle.message("action.description.select.columns.to.see"), null);
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    setPopup(isPopup(e));
    e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = new ArrayList<>();
    if (e != null && !isPopup(e)) {
      actions.add(Separator.create(VcsLogBundle.message("action.title.select.columns.to.see")));
    }
    for (VcsLogColumn column : VcsLogColumn.DYNAMIC_COLUMNS) {
      actions.add(new ToggleColumnAction(column));
    }

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  private static boolean isPopup(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) == null;
  }

  private static boolean isEnabledAndVisible(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    return properties != null && properties.exists(CommonUiProperties.COLUMN_ORDER);
  }

  private static final class ToggleColumnAction extends ToggleAction implements DumbAware {
    private final int myIndex;

    private ToggleColumnAction(@NotNull VcsLogColumn column) {
      super(() -> column.getLocalizedName());
      myIndex = column.ordinal();
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
      VcsLogUsageTriggerCollector.triggerUsage(e, this);

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
