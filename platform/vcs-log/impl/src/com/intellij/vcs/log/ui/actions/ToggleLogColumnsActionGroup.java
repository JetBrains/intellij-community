// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.ui.table.column.VcsLogColumnOrderStorageKt.*;

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
    return properties != null && supportsColumnsReordering(properties);
  }

  private static final class ToggleColumnAction extends ToggleAction implements DumbAware {
    private final VcsLogColumn myColumn;

    private ToggleColumnAction(@NotNull VcsLogColumn column) {
      super(() -> column.getLocalizedName());
      myColumn = column;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      if (properties != null && supportsColumnsReordering(properties)) {
        return getColumnsOrder(properties).contains(myColumn);
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VcsLogUsageTriggerCollector.triggerUsage(e, this);

      VcsLogUiProperties properties = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      assert supportsColumnsReordering(properties);

      if (state) {
        addColumn(properties, myColumn);
      }
      else {
        removeColumn(properties, myColumn);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
    }
  }
}
