// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.GraphSortPresentationUtil;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

public class IntelliSortChooserToggleAction extends ToggleAction implements DumbAware {

  public IntelliSortChooserToggleAction() {
    //noinspection DialogTitleCapitalization
    super(VcsLogBundle.message("vcs.log.action.intellisort.text"),
          VcsLogBundle.message("vcs.log.action.intellisort.description"),
          VcsLogIcons.IntelliSort);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    return properties != null &&
           properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE) &&
           !properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE).equals(PermanentGraph.SortType.Normal);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      PermanentGraph.SortType bekSortType = state ? PermanentGraph.SortType.Bek : PermanentGraph.SortType.Normal;
      properties.set(MainVcsLogUiProperties.BEK_SORT_TYPE, bekSortType);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    Presentation presentation = e.getPresentation();
    presentation.setVisible(BekUtil.isBekEnabled());
    presentation.setEnabled(BekUtil.isBekEnabled() && logUI != null);

    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      String description;
      if (properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.Normal) {
        String localizedDescription = GraphSortPresentationUtil.getLocalizedDescription(PermanentGraph.SortType.Bek);
        description = VcsLogBundle.message("vcs.log.action.turn.intellisort.on", StringUtil.toLowerCase(localizedDescription));
      }
      else {
        String localizedDescription = GraphSortPresentationUtil.getLocalizedDescription(PermanentGraph.SortType.Normal);
        description = VcsLogBundle.message("vcs.log.action.turn.intellisort.off", StringUtil.toLowerCase(localizedDescription));
      }
      presentation.setDescription(description);
    }
    else {
      //noinspection DialogTitleCapitalization
      presentation.setDescription(VcsLogBundle.message("vcs.log.action.intellisort.description"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
