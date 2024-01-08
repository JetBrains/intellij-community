// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.GraphSortPresentationUtil;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class IntelliSortChooserPopupAction extends DefaultActionGroup {
  public IntelliSortChooserPopupAction() {
    super(VcsLogBundle.messagePointer("action.IntelliSortChooserPopupAction.text"),
          VcsLogBundle.messagePointer("action.IntelliSortChooserPopupAction.description"), VcsLogIcons.IntelliSort);
    getTemplatePresentation().setPopupGroup(true);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;

    VcsLogUi logUI = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);

    return Arrays.stream(PermanentGraph.SortType.values())
      .map(sortType -> new SelectIntelliSortTypeAction(logUI, properties, sortType))
      .toArray(AnAction[]::new);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabled(properties != null);
    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      String sortName = GraphSortPresentationUtil.getLocalizedName(properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
      String description = VcsLogBundle.message("vcs.log.action.intellisort.title", sortName);
      e.getPresentation().setDescription(description);
      e.getPresentation().setText(description);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static class SelectIntelliSortTypeAction extends ToggleAction implements DumbAware {
    private final PermanentGraph.SortType mySortType;
    private final VcsLogUi myUI;
    private final VcsLogUiProperties myProperties;

    SelectIntelliSortTypeAction(@NotNull VcsLogUi ui,
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
