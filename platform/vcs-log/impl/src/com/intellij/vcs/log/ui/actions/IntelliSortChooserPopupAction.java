/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class IntelliSortChooserPopupAction extends DumbAwareAction {
  public IntelliSortChooserPopupAction() {
    super("IntelliSort", "Change IntelliSort Type", VcsLogIcons.IntelliSort);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsLogUi logUI = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getRequiredData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);

    ActionGroup settingsGroup = new DefaultActionGroup(ContainerUtil.map(PermanentGraph.SortType.values(),
                                                                         (Function<PermanentGraph.SortType, AnAction>)sortType ->
                                                                           new SelectIntelliSortTypeAction(logUI, properties, sortType)));


    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, settingsGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.MNEMONICS, true,
                              ToolWindowContentUi.POPUP_PLACE);
    Component component = e.getInputEvent().getComponent();
    if (component instanceof ActionButtonComponent) {
      popup.showUnderneathOf(component);
    }
    else {
      popup.showInCenterOf(component);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabled(properties != null);
    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      String description = "IntelliSort: " + properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE).getName();
      e.getPresentation().setDescription(description);
      e.getPresentation().setText(description);
    }
  }

  private static class SelectIntelliSortTypeAction extends ToggleAction implements DumbAware {
    private final PermanentGraph.SortType mySortType;
    private final VcsLogUi myUI;
    private final VcsLogUiProperties myProperties;

    public SelectIntelliSortTypeAction(@NotNull VcsLogUi ui,
                                       @NotNull VcsLogUiProperties properties,
                                       @NotNull PermanentGraph.SortType sortType) {
      super(sortType.getName(), sortType.getDescription() + ".", null);
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
    public boolean isSelected(AnActionEvent e) {
      return myProperties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE) &&
             myProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE).equals(mySortType);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state && myProperties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
        myProperties.set(MainVcsLogUiProperties.BEK_SORT_TYPE, mySortType);
      }
    }
  }
}
