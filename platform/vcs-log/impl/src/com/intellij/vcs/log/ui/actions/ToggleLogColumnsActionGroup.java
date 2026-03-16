// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.column.VcsLogColumn;
import com.intellij.vcs.log.ui.table.column.VcsLogCustomColumn;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.addColumn;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.getDynamicColumns;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.isVisible;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.removeColumn;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.supportsColumnsToggling;

@ApiStatus.Internal
public final class ToggleLogColumnsActionGroup extends ActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    e.getPresentation().setPopupGroup(isPopup(e));
    e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = new ArrayList<>();
    if (e != null && !isPopup(e)) {
      actions.add(Separator.create(VcsLogBundle.message("group.Vcs.Log.ToggleColumns.text")));
    }

    List<VcsLogColumn<?>> dynamicColumns = getDynamicColumns();
    for (VcsLogColumn<?> column : dynamicColumns) {
      actions.add(new ToggleColumnAction(column));
    }

    return actions.toArray(EMPTY_ARRAY);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static boolean isPopup(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) == null;
  }

  private static boolean isEnabledAndVisible(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    return properties != null && supportsColumnsToggling(properties);
  }

  private static final class ToggleColumnAction extends ToggleAction implements DumbAware {
    private final VcsLogColumn<?> myColumn;

    private ToggleColumnAction(@NotNull VcsLogColumn<?> column) {
      super(() -> column.getLocalizedName());
      myColumn = column;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      if (!isColumnAvailable(e)) return false;

      VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      if (properties != null) {
        return isVisible(myColumn, properties);
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VcsLogUsageTriggerCollector.triggerUsage(e, this);

      if (!isColumnAvailable(e)) return;

      VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
      if (properties == null) return;
      assert supportsColumnsToggling(properties);

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

      e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e) && isColumnAvailable(e));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    private boolean isColumnAvailable(@NotNull AnActionEvent e) {
      if (myColumn instanceof VcsLogCustomColumn<?> customColumn) {
        VcsLogData logData = e.getData(VcsLogInternalDataKeys.LOG_DATA);
        return logData != null && VcsLogCustomColumn.isAvailable(customColumn, logData);
      }
      return true;
    }
  }
}
