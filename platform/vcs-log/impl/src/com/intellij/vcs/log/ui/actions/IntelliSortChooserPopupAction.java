// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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

import java.awt.*;

public class IntelliSortChooserPopupAction extends DumbAwareAction {
  public IntelliSortChooserPopupAction() {
    super(VcsLogBundle.messagePointer("action.IntelliSortChooserPopupAction.text"),
          VcsLogBundle.messagePointer("action.IntelliSortChooserPopupAction.description"), VcsLogIcons.IntelliSort);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUi logUI = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);

    ActionGroup settingsGroup = new DefaultActionGroup(ContainerUtil.map(PermanentGraph.SortType.values(),
                                                                         (Function<PermanentGraph.SortType, AnAction>)sortType ->
                                                                           new SelectIntelliSortTypeAction(logUI, properties, sortType)));


    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, settingsGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.MNEMONICS, true,
                              ActionPlaces.TOOLWINDOW_POPUP);
    AutoPopupSupportingListener.installOn(popup);
    Component component = e.getInputEvent().getComponent();
    if (component instanceof ActionButtonComponent) {
      popup.showUnderneathOf(component);
    }
    else {
      popup.showInCenterOf(component);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabled(properties != null);
    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      String sortName = GraphSortPresentationUtil.getLocalizedName(properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
      String description = VcsLogBundle.message("vcs.log.action.intellisort.title", sortName);
      e.getPresentation().setDescription(description);
      e.getPresentation().setText(description);
    }
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
  }
}
