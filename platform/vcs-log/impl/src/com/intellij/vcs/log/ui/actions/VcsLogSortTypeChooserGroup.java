// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.GraphSortPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VcsLogSortTypeChooserGroup extends DefaultActionGroup {

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;

    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (logUI == null) return EMPTY_ARRAY;
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties == null) return EMPTY_ARRAY;

    List<PermanentGraph.SortType> sortTypes = getAvailableSortTypes();

    List<AnAction> actions = new ArrayList<>();
    actions.addAll(ContainerUtil.map(sortTypes, sortType -> {
      return new SelectSortTypeAction(logUI, properties, sortType);
    }));
    return actions.toArray(EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabled(properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static @NotNull List<PermanentGraph.SortType> getAvailableSortTypes() {
    List<PermanentGraph.SortType> sortTypes = new ArrayList<>(PermanentGraph.SortType.getEntries());
    if (!BekUtil.isBekEnabled()) {
      sortTypes.remove(PermanentGraph.SortType.Bek);
    }
    else if (!BekUtil.isLinearBekEnabled()) {
      sortTypes.remove(PermanentGraph.SortType.LinearBek);
    }
    return sortTypes;
  }

  private static class SelectSortTypeAction extends ToggleAction implements DumbAware {
    private final PermanentGraph.SortType mySortType;
    private final VcsLogUi myUI;
    private final VcsLogUiProperties myProperties;

    SelectSortTypeAction(@NotNull VcsLogUi ui,
                         @NotNull VcsLogUiProperties properties,
                         @NotNull PermanentGraph.SortType sortType) {
      super(() -> GraphSortPresentationUtil.getLocalizedName(sortType),
            () -> GraphSortPresentationUtil.getLocalizedDescription(sortType) + ".",
            null);
      myUI = ui;
      myProperties = properties;
      mySortType = sortType;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myUI.getDataPack().isEmpty() && myProperties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myProperties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE) &&
             myProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE).equals(mySortType);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state && myProperties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
        myProperties.set(MainVcsLogUiProperties.BEK_SORT_TYPE, mySortType);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
