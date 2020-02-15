// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class StatusBarPopupActionGroup extends ComputableActionGroup {
  @NotNull
  private final StatusBarWidgetsManager myManager;

  public StatusBarPopupActionGroup(@NotNull StatusBarWidgetsManager manager) {
    super(true);
    myManager = manager;
  }

  @NotNull
  @Override
  protected CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull ActionManager actionManager) {
    return () ->
      CachedValueProvider.Result.create(
        ContainerUtil.map2Array(myManager.getWidgetFactories().keySet(), AnAction.class, ToggleWidgetAction::new), myManager);
  }

  private class ToggleWidgetAction extends DumbAwareToggleAction {
    private final StatusBarWidgetFactory myWidgetFactory;

    private ToggleWidgetAction(@NotNull StatusBarWidgetFactory widgetFactory) {
      super(widgetFactory.getDisplayName());
      myWidgetFactory = widgetFactory;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Project project = e.getProject();
      StatusBar statusBar = e.getData(PlatformDataKeys.STATUS_BAR);
      e.getPresentation().setEnabledAndVisible(project != null && statusBar != null && myManager.canBeEnabledOnStatusBar(myWidgetFactory, statusBar));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      return project.getService(StatusBarWidgetSettings.class).isEnabled(myWidgetFactory);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      project.getService(StatusBarWidgetSettings.class).setEnabled(myWidgetFactory, state);
      if (state) {
        myManager.enableWidget(myWidgetFactory);
      }
      else {
        myManager.disableWidget(myWidgetFactory);
      }
    }
  }
}
