/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.util.List;

public class VcsLogQuickSettingsActions extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogUi logUi = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogSettings settings = ServiceManager.getService(project, VcsLogSettings.class);

    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, new MySettingsActionGroup(settings, logUi), e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, true, ToolWindowContentUi.POPUP_PLACE);
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
    Project project = e.getProject();
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && logUi != null);
  }

  private static class MySettingsActionGroup extends ActionGroup {

    private final VcsLogSettings mySettings;
    private final VcsLogUi myUi;

    public MySettingsActionGroup(VcsLogSettings settings, VcsLogUi ui) {
      mySettings = settings;
      myUi = ui;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      List<AnAction> actions = ContainerUtil.<AnAction>newArrayList(new ShowBranchesPanelAction(), new ShowRootsColumnAction());

      actions.add(new Separator("Highlight"));
      for (VcsLogHighlighterFactory factory : Extensions.getExtensions(VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP, e.getProject())) {
        actions.add(new EnableHighlighterAction(factory));
      }
      return actions.toArray(new AnAction[actions.size()]);
    }

    private class ShowBranchesPanelAction extends ToggleAction implements DumbAware {
      public ShowBranchesPanelAction() {
        super("Show Branches Panel");
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return mySettings.isShowBranchesPanel();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        mySettings.setShowBranchesPanel(state);
        myUi.setBranchesPanelVisible(state);
      }
    }

    private class ShowRootsColumnAction extends ToggleAction implements DumbAware {

      public ShowRootsColumnAction() {
        super("Show Root Names");
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);

        e.getPresentation().setEnabledAndVisible(myUi.isMultipleRoots());
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return myUi.isShowRootNames();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myUi.setShowRootNames(state);
      }
    }

    private class EnableHighlighterAction extends ToggleAction implements DumbAware {
      private final VcsLogHighlighterFactory myFactory;

      private EnableHighlighterAction(VcsLogHighlighterFactory factory) {
        super(factory.getDescription());
        myFactory = factory;
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return myUi.isHighlighterEnabled(myFactory.getId());
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myUi.enableHighlighter(myFactory.getId(), state);
      }
    }
  }
}
